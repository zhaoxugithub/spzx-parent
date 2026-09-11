const fs = require("fs");
const files = [
  "C:/Users/11931/IdeaProjects/spzx-parent/.ua/intermediate/batch-30-part-1.json",
  "C:/Users/11931/IdeaProjects/spzx-parent/.ua/intermediate/batch-30-part-2.json"
];
let totalNodes = 0, totalEdges = 0;
for (const f of files) {
  const obj = JSON.parse(fs.readFileSync(f, "utf8"));
  const ids = new Set();
  for (const n of obj.nodes) {
    if (ids.has(n.id)) throw new Error("DUPLICATE node id in " + f + ": " + n.id);
    ids.add(n.id);
  }
  const badEdges = [];
  for (const e of obj.edges) {
    if (e.source === e.target) badEdges.push("SELF " + e.source);
    if (!ids.has(e.source)) badEdges.push("SOURCE-MISSING " + e.source + " -> " + e.target);
    if (!ids.has(e.target)) badEdges.push("TARGET-MISSING " + e.source + " -> " + e.target);
  }
  console.log(f.split("/").pop() + " nodes=" + obj.nodes.length + " edges=" + obj.edges.length + " nodeIds=" + ids.size + " badEdges=" + badEdges.length);
  if (badEdges.length) console.log(badEdges.join("\n"));
  totalNodes += obj.nodes.length;
  totalEdges += obj.edges.length;
}
console.log("TOTAL nodes=" + totalNodes + " edges=" + totalEdges);
