#!/usr/bin/env python3
"""
NetworkPeer Documentation PDF Builder
Converts all markdown files in docs/ to styled PDFs using pandoc + weasyprint.
Output: docs/pdfs/ with one flat PDF folder.
"""

import os
import sys
import subprocess
import shutil
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed

# Configuration
DOCS_ROOT = Path(__file__).parent.parent / "docs"
PDF_ROOT = DOCS_ROOT / "pdfs"
EXCLUDE_DIRS = {"pdfs", ".git", "node_modules"}

# CSS for professional styling
PDF_CSS = """
@page {
    size: A4;
    margin: 2.5cm 2cm;
    @top-center { content: "NetworkPeer Technical Documentation"; font-size: 9pt; color: #666; }
    @bottom-center { content: "Page " counter(page) " of " counter(pages); font-size: 9pt; color: #666; }
    @bottom-right { content: "v1.3 | Confidential"; font-size: 8pt; color: #999; }
}

body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    font-size: 11pt;
    line-height: 1.6;
    color: #1a1a2e;
    max-width: none;
}

h1, h2, h3, h4, h5, h6 {
    color: #0f172a;
    font-weight: 600;
    line-height: 1.3;
    margin-top: 1.5em;
    margin-bottom: 0.5em;
    page-break-after: avoid;
}

h1 { font-size: 24pt; border-bottom: 2px solid #2563eb; padding-bottom: 0.3em; }
h2 { font-size: 18pt; border-bottom: 1px solid #e2e8f0; padding-bottom: 0.2em; }
h3 { font-size: 14pt; color: #1e293b; }
h4 { font-size: 12pt; color: #334155; }

p { margin: 0.5em 0; text-align: justify; }

code {
    font-family: "SF Mono", "Fira Code", "Monaco", "Consolas", monospace;
    font-size: 0.9em;
    background: #f1f5f9;
    padding: 0.15em 0.4em;
    border-radius: 3px;
}

pre {
    background: #0f172a;
    color: #e2e8f0;
    padding: 1em;
    border-radius: 6px;
    overflow-x: auto;
    page-break-inside: avoid;
    margin: 1em 0;
}

pre code {
    background: transparent;
    padding: 0;
    font-size: 0.85em;
    line-height: 1.5;
}

table {
    width: 100%;
    border-collapse: collapse;
    margin: 1em 0;
    page-break-inside: avoid;
    font-size: 10pt;
}

th, td {
    border: 1px solid #e2e8f0;
    padding: 0.5em 0.75em;
    text-align: left;
}

th {
    background: #f8fafc;
    font-weight: 600;
    color: #0f172a;
}

tr:nth-child(even) td { background: #fafbfc; }

blockquote {
    border-left: 4px solid #2563eb;
    padding-left: 1em;
    margin: 1em 0;
    color: #475569;
    font-style: italic;
}

ul, ol { padding-left: 1.5em; }
li { margin: 0.25em 0; }

a { color: #2563eb; text-decoration: none; }
a:hover { text-decoration: underline; }

hr { border: none; border-top: 1px solid #e2e8f0; margin: 2em 0; }

/* Syntax highlighting (via pygments) */
.highlight { background: #0f172a; }
.highlight .c { color: #64748b; }      /* Comment */
.highlight .k { color: #c084fc; }      /* Keyword */
.highlight .s { color: #fca5a5; }      /* String */
.highlight .n { color: #e2e8f0; }      /* Name */
.highlight .o { color: #fcd34d; }      /* Operator */
.highlight .p { color: #e2e8f0; }      /* Punctuation */

.toc { page-break-after: always; }
.toc ul { list-style: none; padding-left: 1em; }
.toc a { text-decoration: none; color: #1e293b; }
"""

def check_dependencies():
    """Verify required tools are installed."""
    tools = {
        "pandoc": "pandoc --version",
        "weasyprint": "weasyprint --version",
    }
    missing = []
    for name, cmd in tools.items():
        try:
            subprocess.run(cmd.split(), capture_output=True, check=True)
            print(f"✅ {name} available")
        except (subprocess.CalledProcessError, FileNotFoundError):
            missing.append(name)
            print(f"❌ {name} NOT found")
    if missing:
        print(f"\nInstall missing tools:")
        for m in missing:
            if m == "pandoc":
                print("  brew install pandoc")
            elif m == "weasyprint":
                print("  pip install weasyprint")
        return False
    return True

def find_markdown_files():
    """Recursively find all .md files excluding legacy/ and pdfs/."""
    md_files = []
    for root, dirs, files in os.walk(DOCS_ROOT):
        # Skip excluded directories
        dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
        for f in files:
            if f.endswith(".md"):
                full = Path(root) / f
                rel = full.relative_to(DOCS_ROOT)
                md_files.append((full, rel))
    return md_files

def convert_md_to_pdf(md_path: Path, rel_path: Path):
    """Convert single markdown file to PDF using pandoc + weasyprint."""
    pdf_path = PDF_ROOT / rel_path.with_suffix(".pdf").name
    pdf_path.parent.mkdir(parents=True, exist_ok=True)

    # Write CSS to temp file
    css_file = pdf_path.with_suffix(".css")
    css_file.write_text(PDF_CSS)

    try:
        # Step 1: pandoc MD -> HTML (with TOC, syntax highlighting)
        html_file = pdf_path.with_suffix(".html")
        pandoc_cmd = [
            "pandoc",
            str(md_path),
            "-o", str(html_file),
            "--standalone",
            "--toc",
            "--toc-depth=3",
            "--syntax-highlighting=pygments",
            f"--metadata=title:{rel_path.stem.replace('_', ' ').title()}",
            f"--css={css_file}",
            "--shift-heading-level-by=0",
        ]
        result = subprocess.run(pandoc_cmd, capture_output=True, text=True, timeout=60)
        if result.returncode != 0:
            return False, f"pandoc failed: {result.stderr}"

        # Step 2: weasyprint HTML -> PDF
        weasy_cmd = ["weasyprint", str(html_file), str(pdf_path)]
        result = subprocess.run(weasy_cmd, capture_output=True, text=True, timeout=60)
        if result.returncode != 0:
            return False, f"weasyprint failed: {result.stderr}"

        # Cleanup temp files
        html_file.unlink(missing_ok=True)
        css_file.unlink(missing_ok=True)

        return True, f"✅ {rel_path} → {pdf_path.relative_to(PDF_ROOT)}"

    except subprocess.TimeoutExpired:
        return False, f"Timeout converting {rel_path}"
    except Exception as e:
        return False, f"Error: {e}"

def main():
    print("=" * 60)
    print("NetworkPeer Documentation PDF Builder")
    print("=" * 60)

    if not check_dependencies():
        sys.exit(1)

    # Keep the output folder stable and remove only generated artifacts.
    PDF_ROOT.mkdir(parents=True, exist_ok=True)
    for generated in PDF_ROOT.glob("*"):
        if generated.is_file() and generated.suffix in {".pdf", ".html", ".css"}:
            generated.unlink()

    # Find markdown files
    md_files = find_markdown_files()
    print(f"\nFound {len(md_files)} markdown files to convert\n")

    # Convert in parallel
    results = []
    with ThreadPoolExecutor(max_workers=4) as executor:
        futures = {
            executor.submit(convert_md_to_pdf, md, rel): (md, rel)
            for md, rel in md_files
        }
        for future in as_completed(futures):
            md, rel = futures[future]
            success, msg = future.result()
            results.append((rel, success, msg))
            print(msg)

    # Summary
    print("\n" + "=" * 60)
    print("CONVERSION SUMMARY")
    print("=" * 60)
    success_count = sum(1 for _, s, _ in results if s)
    fail_count = len(results) - success_count
    for rel, success, msg in sorted(results):
        status = "✅" if success else "❌"
        print(f"  {status} {rel}")
    print(f"\nTotal: {len(results)} | Success: {success_count} | Failed: {fail_count}")

    if fail_count > 0:
        sys.exit(1)

    print(f"\n📁 All PDFs saved to: {PDF_ROOT}")
    print("   Open with: open " + str(PDF_ROOT))

if __name__ == "__main__":
    main()