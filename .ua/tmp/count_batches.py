import glob, os, re, sys
r = sys.argv[1]
fs = glob.glob(os.path.join(r, ".ua", "intermediate", "batch-*.json"))
done = set()
for f in fs:
    b = os.path.basename(f)
    m = re.match(r"^batch-(\d+)(?:-part-\d+)?\.json$", b)
    if m:
        done.add(int(m.group(1)))
miss = [i for i in range(36) if i not in done]
print("batch files:", len(fs), "| logical batches done:", len(done))
print("missing:", sorted(miss))
