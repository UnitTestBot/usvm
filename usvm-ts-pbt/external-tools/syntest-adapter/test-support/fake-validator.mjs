const suffix = process.argv.slice(-3);
if (suffix[0] !== "validate" || suffix[1] !== "raw-run" || !suffix[2]) {
  process.stderr.write("unexpected validator arguments\n");
  process.exitCode = 2;
} else {
  process.stdout.write(`${JSON.stringify({ artifact: "raw-run-directory", valid: true })}\n`);
}
