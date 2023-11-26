package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"go/token"
	"io"
	"log"
	"os"
	"path"
	"strings"
	"time"

	"golang.org/x/tools/go/packages"
	"golang.org/x/tools/go/ssa"
	"golang.org/x/tools/go/ssa/ssautil"
	"gopkg.in/yaml.v3"
)

type Config struct {
	EnableTracing bool `yaml:"enable_tracing"`

	DumpSSA         bool   `yaml:"dump_ssa"`
	DumpSSAFileName string `yaml:"dump_ssa_file_name"`
}

type SSA struct {
	output io.WriteCloser

	program  *ssa.Program
	packages []*ssa.Package
}

func NewSSA(packageName string, cfg Config) (*SSA, error) {
	mode := packages.NeedName |
		packages.NeedFiles |
		packages.NeedCompiledGoFiles |
		packages.NeedImports |
		packages.NeedDeps |
		packages.NeedExportFile |
		packages.NeedTypes |
		packages.NeedTypesSizes |
		packages.NeedTypesInfo |
		packages.NeedSyntax |
		packages.NeedModule |
		packages.NeedEmbedFiles |
		packages.NeedEmbedPatterns
	packageCfg := &packages.Config{Mode: mode}
	if cfg.EnableTracing {
		packageCfg.Logf = log.Printf
	}

	initialPackages, err := packages.Load(packageCfg, packageName)
	if err != nil {
		return nil, err
	}
	if len(initialPackages) == 0 {
		return nil, fmt.Errorf("no packages were loaded")
	}
	if packages.PrintErrors(initialPackages) > 0 {
		return nil, fmt.Errorf("packages contain errors")
	}

	program, _ := ssautil.AllPackages(initialPackages, ssa.InstantiateGenerics|ssa.SanityCheckFunctions)
	program.Build()

	s := &SSA{
		output:   os.Stdout,
		program:  program,
		packages: program.AllPackages(),
	}

	if cfg.DumpSSA {
		if cfg.DumpSSAFileName != "" {
			CheckError(os.MkdirAll(path.Dir(cfg.DumpSSAFileName), os.ModePerm))
			s.output, err = os.Create(cfg.DumpSSAFileName)
			if err != nil {
				return nil, err
			}
			defer CheckClose(s.output)
		}
		s.dump()
	}

	return s, nil
}

func (s *SSA) Write() {
	for _, pkg := range s.packages {
		now := time.Now()
		s.writePackage(pkg)
		log.Printf("%s serialized! Took %.3f seconds\n", pkg.String(), time.Since(now).Seconds())
	}
}

func (s *SSA) writePackage(p *ssa.Package) {
	for _, ext := range []string{".yaml", ".json"} {
		CheckError(os.MkdirAll(OutputDir, os.ModePerm))
		output, err := os.Create(path.Join(OutputDir, strings.ReplaceAll(p.Pkg.Path(), "/", "_")+ext))
		CheckError(err)

		buf := &bytes.Buffer{}
		pkg := PackPackage(p)
		if ext == ".yaml" {
			CheckError(yaml.NewEncoder(buf).Encode(pkg))
		} else {
			enc := json.NewEncoder(buf)
			enc.SetEscapeHTML(false)
			CheckError(enc.Encode(pkg))
			buf.Truncate(buf.Len() - 1) // remove newline character
		}
		CheckError(output.Write(buf.Bytes()))
		CheckClose(output)
	}
}

func (s *SSA) dump() {
	out := &bytes.Buffer{}
	for _, pkg := range s.packages {
		ssa.WritePackage(out, pkg)
		for _, object := range pkg.Members {
			if object.Token() == token.FUNC {
				ssa.WriteFunction(out, pkg.Func(object.Name()))
			}
		}
	}
	CheckError(s.output.Write(out.Bytes()))
}
