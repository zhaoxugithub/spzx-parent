const fs = require("fs");
const files = process.argv.slice(2);
let totalNodes = 0, totalEdges = 0;
for (const f of files) {
  const raw = fs.readFileSync(f, "utf8");
  let j;
  try { j = JSON.parse(raw); } catch (e) { console.log("PARSE ERROR " + f + ": " + e.message); continue; }
  const ids = j.nodes.map((n) => n.id);
  const dup = [...new Set(ids.filter((id, i) => ids.indexOf(id) !== i))];
  const idSet = new Set(ids);
  const dangling = [];
  for (const e of j.edges) {
    if (!idSet.has(e.source)) dangling.push("SRC:" + e.source);
    if (!idSet.has(e.target)) dangling.push("TGT:" + e.target);
  }
  console.log("--- " + f);
  console.log("nodes=" + j.nodes.length + " edges=" + j.edges.length);
  console.log("dupIds=" + dup.join("; "));
  console.log("dangling=" + [...new Set(dangling)].join("; "));
  totalNodes += j.nodes.length;
  totalEdges += j.edges.length;
}
console.log("TOTAL nodes=" + totalNodes + " edges=" + totalEdges);
