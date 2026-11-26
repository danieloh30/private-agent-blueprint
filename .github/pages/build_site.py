"""Build the static tutorial and documentation site for GitHub Pages.

Pages:
  docs/tutorials/index.md          -> index.html (landing page)
  docs/tutorials/NN-<slug>.md      -> tutorials/NN-<slug>.html (numbered tutorials, in order)
  README.md                        -> readme.html
  docs/**/*.md (everything else)   -> docs/**/*.html

Each page embeds its Markdown source and renders it in the browser with marked; ```mermaid blocks
are rendered by mermaid. Both libraries are pinned (exact version + Subresource Integrity) in
template.html. Navigation (tutorials in order, then a Reference group) and previous/next links are
generated here. Standard library only.

Relative links: to a published .md page (or to a directory with a README.md) -> the generated page;
to any other repository file -> that file on GitHub (blob view at the built commit).

Usage: python3 -I .github/pages/build_site.py <repo-root> <output-dir>
Env: GITHUB_SERVER_URL, GITHUB_REPOSITORY, GITHUB_SHA (for links to repository files). Outside
GitHub Actions the links point at the default repository's main branch.
"""

import html
import os
import posixpath
import re
import sys
from pathlib import Path

PRODUCT = "Your Code Never Leaves"
DEFAULT_REPOSITORY = "danieloh30/private-agent-blueprint"
LINK = re.compile(r"(\]\()([^)\s]+)((?:\s+\"[^\"]*\")?\))")
TUTORIAL = re.compile(r"^docs/tutorials/(\d\d)-[a-z0-9-]+\.md$")

# Reference group, in display order: (source, label). Missing files are skipped.
REFERENCE = [
    ("README.md", "README"),
    ("docs/runbook.md", "Presenter runbook"),
    ("docs/configuration.md", "Configuration"),
    ("docs/threat-model.md", "Threat model"),
    ("docs/troubleshooting.md", "Reset and troubleshooting"),
]


def page_for(src: str) -> str:
    if src == "docs/tutorials/index.md":
        return "index.html"
    if TUTORIAL.match(src):
        return "tutorials/" + posixpath.basename(src)[:-3] + ".html"
    if src == "README.md":
        return "readme.html"
    return src[:-3] + ".html"


def heading(text: str, fallback: str) -> str:
    m = re.search(r"^#\s+(.+)$", text, re.M)
    return m.group(1).strip() if m else fallback


def main() -> int:
    root = Path(sys.argv[1]).resolve()
    out = Path(sys.argv[2]).resolve()
    template = (Path(__file__).parent / "template.html").read_text(encoding="utf-8")

    sources = []
    if (root / "README.md").is_file():
        sources.append("README.md")
    sources += sorted(p.relative_to(root).as_posix() for p in (root / "docs").rglob("*.md"))
    published = {s: page_for(s) for s in sources}
    texts = {s: (root / s).read_text(encoding="utf-8") for s in sources}
    titles = {s: heading(texts[s], s) for s in sources}

    tutorials = sorted(s for s in sources if TUTORIAL.match(s))
    landing = "docs/tutorials/index.md" if "docs/tutorials/index.md" in published else None
    reference = [(s, label) for s, label in REFERENCE if s in published]

    server = os.environ.get("GITHUB_SERVER_URL", "https://github.com")
    repo = os.environ.get("GITHUB_REPOSITORY") or DEFAULT_REPOSITORY
    ref = os.environ.get("GITHUB_SHA") or "main"
    repo_url = f"{server}/{repo}"
    blob_base = f"{repo_url}/blob/{ref}/"

    for src in sources:
        page = published[src]
        src_dir = posixpath.dirname(src)
        page_dir = posixpath.dirname(page) or "."

        def rel(target_page: str) -> str:
            return posixpath.relpath(target_page, page_dir)

        def rewrite(m: re.Match) -> str:
            target = m.group(2)
            if re.match(r"^([a-z][a-z0-9+.-]*:|#|/)", target, re.I):
                return m.group(0)
            path, _, frag = target.partition("#")
            resolved = posixpath.normpath(posixpath.join(src_dir, path)) if path else src
            if resolved in published:
                new = rel(published[resolved])
            elif posixpath.join(resolved, "README.md") in published:
                new = rel(published[posixpath.join(resolved, "README.md")])
            elif posixpath.join(resolved, "index.md") in published:
                new = rel(published[posixpath.join(resolved, "index.md")])
            elif not resolved.startswith(".."):
                new = blob_base + resolved
            else:
                return m.group(0)
            return f"{m.group(1)}{new}{'#' + frag if frag else ''}{m.group(3)}"

        text = LINK.sub(rewrite, texts[src])
        title = titles[src]

        def item(s: str, label: str) -> str:
            current = ' aria-current="page"' if s == src else ""
            return f'<li><a href="{html.escape(rel(published[s]))}"{current}>{html.escape(label)}</a></li>'

        nav = ['<p class="nav-group">Tutorials</p><ol class="nav-list">']
        if landing:
            nav.append(item(landing, "Start here"))
        for s in tutorials:
            nav.append(item(s, titles[s]))
        nav.append('</ol><p class="nav-group">Reference</p><ul class="nav-list">')
        for s, label in reference:
            nav.append(item(s, label))
        nav.append("</ul>")

        kicker = "Reference"
        pager = ""
        if src == landing or src in tutorials:
            order = ([landing] if landing else []) + tutorials
            i = order.index(src)
            kicker = "Tutorials" if src == landing else f"Tutorial {i if landing else i + 1} of {len(tutorials)}"
            links = []
            if i > 0:
                p = order[i - 1]
                links.append(f'<a class="pager-prev" href="{html.escape(rel(published[p]))}" rel="prev">'
                             f'<span class="pager-label">Previous</span>'
                             f'<span class="pager-title">{html.escape(titles[p])}</span></a>')
            if i + 1 < len(order):
                n = order[i + 1]
                links.append(f'<a class="pager-next" href="{html.escape(rel(published[n]))}" rel="next">'
                             f'<span class="pager-label">Next</span>'
                             f'<span class="pager-title">{html.escape(titles[n])}</span></a>')
            pager = '<nav class="pager" aria-label="Tutorial pages">' + "".join(links) + "</nav>"

        page_title = PRODUCT if src == landing else f"{title} · {PRODUCT}"
        body = (
            template.replace("{{TITLE}}", html.escape(page_title))
            .replace("{{PRODUCT}}", html.escape(PRODUCT))
            .replace("{{HOME}}", html.escape(rel("index.html")))
            .replace("{{KICKER}}", html.escape(kicker))
            .replace("{{REPO_URL}}", html.escape(repo_url))
            .replace("{{SOURCE_URL}}", html.escape(blob_base + src))
            .replace("{{NAV}}", "".join(nav))
            .replace("{{PAGER}}", pager)
            # Keep the embedded source from closing its <script> element.
            .replace("{{MARKDOWN}}", text.replace("</script", "<\\/script"))
        )
        dest = out / page
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(body, encoding="utf-8")
        print(f"{src} -> {page}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
