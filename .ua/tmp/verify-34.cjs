const fs = require('fs');
const base = 'C:/Users/11931/IdeaProjects/spzx-parent/.ua/intermediate/';
const g1 = JSON.parse(fs.readFileSync(base + 'batch-34-part-1.json', 'utf8'));
const g2 = JSON.parse(fs.readFileSync(base + 'batch-34-part-2.json', 'utf8'));
const merged = { nodes: [...g1.nodes, ...g2.nodes], edges: [...g1.edges, ...g2.edges] };
const ids = new Set(merged.nodes.map(n => n.id));
const dupNode = merged.nodes.length - ids.size;
const dangling = merged.edges.filter(e => !ids.has(e.source) || !ids.has(e.target)).length;
const dupEdge = merged.edges.length - new Set(merged.edges.map(e => e.source + '|' + e.type + '|' + e.target)).size;
const badReq = merged.nodes.filter(n => !n.id || !n.type || !n.name || !n.summary || !n.tags || !n.tags.length || !n.complexity ||
  ((n.type === 'class' || n.type === 'function') && !n.lineRange) ||
  ((n.type === 'file' || n.type === 'config') && !n.filePath)).length;
const typeCount = {}; merged.nodes.forEach(n => typeCount[n.type] = (typeCount[n.type] || 0) + 1);
const edgeTypeCount = {}; merged.edges.forEach(e => edgeTypeCount[e.type] = (edgeTypeCount[e.type] || 0) + 1);
// self-check imports count vs batchImportData sum
const sumImports = Object.values(require('fs').readFileSync('C:/Users/11931/IdeaProjects/spzx-parent/.ua/tmp/ua-file-analyzer-input-34.json', 'utf8') ? (()=>{ const j=JSON.parse(fs.readFileSync('C:/Users/11931/IdeaProjects/spzx-parent/.ua/tmp/ua-file-analyzer-input-34.json','utf8')); return j.batchImportData; })() : {}).reduce((a,v)=>a+v.length,0);
const importsEdges = merged.edges.filter(e => e.type === 'imports').length;
console.log(JSON.stringify({
  batch: 34, partFiles: 2,
  totalNodes: merged.nodes.length, totalEdges: merged.edges.length,
  nodeTypes: typeCount, edgeTypes: edgeTypeCount,
  dupNodes: dupNode, dupEdges: dupEdge, danglingEdges: dangling, nodesMissingRequired: badReq,
  importsEdges: importsEdges, batchImportDataSum: sumImports,
  skippedFiles: ['spzx-modules/spzx-user/pom.xml']
}, null, 2));
