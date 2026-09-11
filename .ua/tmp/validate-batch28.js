const fs = require("fs");
for (const f of process.argv.slice(2)) {
  try {
    const j = JSON.parse(fs.readFileSync(f, "utf8"));
    const ids = j.nodes.map(n => n.id);
    const seen = new Set();
    let dup = [];
    for (const id of ids) { if (seen.has(id)) dup.push(id); seen.add(id); }
    let edgesBad = 0;
    for (const e of j.edges) { if (!e.source || !e.target || !e.type || !e.direction || typeof e.weight !== "number") edgesBad++; }
    console.log(f.split(/[\\/]/).pop() + " nodes=" + j.nodes.length + " edges=" + j.edges.length + " dupIds=" + (dup.length ? dup.join(",") : "none") + " malformedEdges=" + edgesBad);
  } catch (e) {
    console.log(f.split(/[\\/]/).pop() + " INVALID: " + e.message);
  }
}
