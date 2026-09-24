//= require govuk_tech_docs

// Render ```mermaid fences. The tech docs gem highlights them as plain text.
// The source in the page stays the editable diagram.
(function () {
  var MERMAID_SRC = "https://cdn.jsdelivr.net/npm/mermaid@11.12.0/dist/mermaid.min.js";
  var START = /^(?:stateDiagram(?:-v2)?|flowchart|sequenceDiagram|graph|classDiagram|erDiagram|journey|gantt|pie|gitGraph|mindmap|timeline|quadrantChart|C4Context)\b/;

  function mermaidBlocks() {
    return Array.prototype.filter.call(document.querySelectorAll("pre code"), function (code) {
      return START.test(code.textContent.trim());
    });
  }

  function render() {
    if (!window.mermaid) return;
    var blocks = mermaidBlocks();
    if (!blocks.length) return;
    window.mermaid.initialize({ startOnLoad: false, securityLevel: "strict" });
    var nodes = blocks.map(function (code) {
      var div = document.createElement("div");
      div.className = "mermaid";
      div.textContent = code.textContent;
      code.parentElement.replaceWith(div);
      return div;
    });
    window.mermaid.run({ nodes: nodes });
  }

  document.addEventListener("DOMContentLoaded", function () {
    if (!mermaidBlocks().length) return;
    var script = document.createElement("script");
    script.src = MERMAID_SRC;
    script.onload = render;
    document.head.appendChild(script);
  });
})();
