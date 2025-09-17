#!/bin/bash

# TypeScript Reachability Analysis CLI Wrapper
# This script makes it easy to run reachability analysis on TypeScript projects

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
USVM_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Default values
PROJECT_PATH=""
TARGETS_FILE=""
OUTPUT_DIR="./reachability-results"
MODE="PUBLIC_METHODS"
SOLVER="YICES"
TIMEOUT=300
STEPS=3500
VERBOSE=false
INCLUDE_STATEMENTS=false
METHOD_FILTER=""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

usage() {
    echo -e "${BLUE}🎯 TypeScript Reachability Analysis Tool${NC}"
    echo ""
    echo "Usage: $0 -p PROJECT_PATH [OPTIONS]"
    echo ""
    echo "Required:"
    echo "  -p, --project PATH         TypeScript project directory"
    echo ""
    echo "Optional:"
    echo "  -t, --targets FILE         JSON file with target definitions"
    echo "  -o, --output DIR           Output directory (default: ./reachability-results)"
    echo "  -m, --mode MODE            Analysis mode: ALL_METHODS, PUBLIC_METHODS, ENTRY_POINTS"
    echo "      --method PATTERN       Filter methods by name pattern"
    echo "      --solver SOLVER        SMT solver: YICES, Z3, CVC5 (default: YICES)"
    echo "      --timeout SECONDS      Analysis timeout (default: 300)"
    echo "      --steps LIMIT          Max steps limit (default: 3500)"
    echo "  -v, --verbose              Enable verbose output"
    echo "      --include-statements   Include statement details in output"
    echo "  -h, --help                 Show this help message"
    echo ""
    echo "Examples:"
    echo "  # Analyze all public methods in a TypeScript project"
    echo "  $0 -p ./my-typescript-project"
    echo ""
    echo "  # Use custom targets and verbose output"
    echo "  $0 -p ./my-project -t ./targets.json -v"
    echo ""
    echo "  # Analyze specific methods with detailed output"
    echo "  $0 -p ./project --method ProcessManager --include-statements"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -p|--project)
            PROJECT_PATH="$2"
            shift 2
            ;;
        -t|--targets)
            TARGETS_FILE="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        -m|--mode)
            MODE="$2"
            shift 2
            ;;
        --method)
            METHOD_FILTER="$2"
            shift 2
            ;;
        --solver)
            SOLVER="$2"
            shift 2
            ;;
        --timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        --steps)
            STEPS="$2"
            shift 2
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        --include-statements)
            INCLUDE_STATEMENTS=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo -e "${RED}❌ Unknown option: $1${NC}"
            usage
            exit 1
            ;;
    esac
done

# Validate required arguments
if [[ -z "$PROJECT_PATH" ]]; then
    echo -e "${RED}❌ Error: Project path is required${NC}"
    usage
    exit 1
fi

if [[ ! -d "$PROJECT_PATH" ]]; then
    echo -e "${RED}❌ Error: Project path does not exist: $PROJECT_PATH${NC}"
    exit 1
fi

# Display configuration
echo -e "${BLUE}🚀 Starting TypeScript Reachability Analysis${NC}"
echo "📁 Project: $PROJECT_PATH"
echo "📄 Output: $OUTPUT_DIR"
echo "🔍 Mode: $MODE"
echo "⚙️ Solver: $SOLVER"

# Path to the compiled shadow JAR
JAR_PATH="$USVM_ROOT/usvm-ts/build/libs/usvm-ts-all.jar"

# Check if JAR exists, if not try to build it
if [[ ! -f "$JAR_PATH" ]]; then
    echo -e "${YELLOW}⚠️ JAR not found, attempting to build...${NC}"
    cd "$USVM_ROOT"
    if ! ./gradlew :usvm-ts:shadowJar; then
        echo -e "${RED}❌ Error: Failed to build JAR${NC}"
        exit 1
    fi
fi

# Build the command arguments
JAVA_ARGS=()
JAVA_ARGS+=("--project" "$PROJECT_PATH")
JAVA_ARGS+=("--output" "$OUTPUT_DIR")
JAVA_ARGS+=("--mode" "$MODE")
JAVA_ARGS+=("--solver" "$SOLVER")
JAVA_ARGS+=("--timeout" "$TIMEOUT")
JAVA_ARGS+=("--steps" "$STEPS")

if [[ -n "$TARGETS_FILE" ]]; then
    if [[ ! -f "$TARGETS_FILE" ]]; then
        echo -e "${RED}❌ Error: Targets file does not exist: $TARGETS_FILE${NC}"
        exit 1
    fi
    JAVA_ARGS+=("--targets" "$TARGETS_FILE")
    echo "📋 Targets: $TARGETS_FILE"
fi

if [[ -n "$METHOD_FILTER" ]]; then
    JAVA_ARGS+=("--method" "$METHOD_FILTER")
    echo "🎯 Method filter: $METHOD_FILTER"
fi

if [[ "$VERBOSE" == true ]]; then
    JAVA_ARGS+=("--verbose")
    echo "📝 Verbose mode enabled"
fi

if [[ "$INCLUDE_STATEMENTS" == true ]]; then
    JAVA_ARGS+=("--include-statements")
    echo "📍 Including statement details"
fi

echo ""
echo -e "${YELLOW}⚡ Running analysis...${NC}"

# Execute the analysis
java -Dfile.encoding=UTF-8 \
     -Dsun.stdout.encoding=UTF-8 \
     -cp "$JAR_PATH" \
     org.usvm.reachability.cli.ReachabilityKt \
     "${JAVA_ARGS[@]}"

echo ""
echo -e "${GREEN}✅ Analysis complete! Check the results in: $OUTPUT_DIR${NC}"
