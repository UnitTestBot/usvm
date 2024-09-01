package main

import (
	"flag"
	"log"
	"time"
)

var (
	packageName   = flag.String("packageName", "usvm/examples", "Full package name")
	enableTracing = flag.Bool("enable-tracing", false, "Enables tracing via Logf")

	dumpSSA     = flag.Bool("dump-ssa", true, "Dumps SSA")
	dumpSSAFile = flag.String("dump-ssa-file", "dump/ssadump.txt", "SSA output file")

	dryRun = flag.Bool("dry-run", false, "Dry run")
)

func main() {
	now := time.Now()
	flag.Parse()

	if *packageName == "" {
		log.Fatal("Fatal: missing package name")
	}

	config := Config{
		EnableTracing:   *enableTracing,
		DumpSSA:         *dumpSSA,
		DumpSSAFileName: *dumpSSAFile,
	}

	ssaInfo, err := NewSSA(*packageName, config)
	CheckError(err)

	if !*dryRun {
		ssaInfo.Write()
	}

	log.Printf("Done! Took %.3f seconds\n", time.Since(now).Seconds())
}
