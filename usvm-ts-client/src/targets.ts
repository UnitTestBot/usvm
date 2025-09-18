import {
  LocationDto,
  TargetDto,
  TargetTreeNodeDto,
  TargetsContainerDto,
  TargetTypeDto,
  TreeTrace,
  LinearTrace
} from './types';

export class TargetBuilder {
  private traces: (TreeTrace | LinearTrace)[] = [];

  /**
   * Add a method target as a tree trace
   */
  addMethodAsTree(
    className: string,
    methodName: string,
    fileName: string,
    options?: {
      type?: TargetTypeDto;
      stmtType?: string;
      block?: number;
      index?: number;
      children?: TargetTreeNodeDto[];
    }
  ): this {
    const target: TargetDto = {
      type: options?.type || TargetTypeDto.INTERMEDIATE,
      location: {
        fileName,
        className,
        methodName,
        stmtType: options?.stmtType,
        block: options?.block,
        index: options?.index
      }
    };

    const treeTrace: TreeTrace = {
      root: {
        target,
        children: options?.children || []
      }
    };

    this.traces.push(treeTrace);
    return this;
  }

  /**
   * Add a complete tree trace with hierarchical structure
   */
  addTreeTrace(rootTarget: TargetDto, children: TargetTreeNodeDto[] = []): this {
    const treeTrace: TreeTrace = {
      root: {
        target: rootTarget,
        children
      }
    };

    this.traces.push(treeTrace);
    return this;
  }

  /**
   * Add a linear sequence of targets as a linear trace
   */
  addLinearTrace(targets: TargetDto[]): this {
    const linearTrace: LinearTrace = {
      targets
    };

    this.traces.push(linearTrace);
    return this;
  }

  /**
   * Create a linear sequence of targets and add as tree trace
   * (each target has single child, forming a chain)
   */
  addLinearAsTree(targets: TargetDto[]): this {
    if (targets.length === 0) return this;

    // Build the chain from the end backwards
    let currentNode: TargetTreeNodeDto | undefined;

    for (let i = targets.length - 1; i >= 0; i--) {
      currentNode = {
        target: targets[i],
        children: currentNode ? [currentNode] : []
      };
    }

    if (currentNode) {
      const treeTrace: TreeTrace = {
        root: currentNode
      };
      this.traces.push(treeTrace);
    }

    return this;
  }

  /**
   * Build the final targets container
   * If only one trace, return it directly. Otherwise return array of traces.
   */
  build(): TargetsContainerDto {
    if (this.traces.length === 1) {
      return this.traces[0];
    }
    return this.traces;
  }

  /**
   * Clear all traces
   */
  clear(): this {
    this.traces = [];
    return this;
  }

  /**
   * Get current trace count
   */
  get count(): number {
    return this.traces.length;
  }
}

/**
 * Utility functions for working with targets
 */
export class TargetUtils {
  /**
   * Create a location with required fields
   */
  static createLocation(
    fileName: string,
    className: string,
    methodName: string,
    options?: {
      stmtType?: string;
      block?: number;
      index?: number;
    }
  ): LocationDto {
    return {
      fileName,
      className,
      methodName,
      stmtType: options?.stmtType,
      block: options?.block,
      index: options?.index
    };
  }

  /**
   * Create a target with proper location
   */
  static createTarget(
    fileName: string,
    className: string,
    methodName: string,
    type: TargetTypeDto = TargetTypeDto.INTERMEDIATE,
    options?: {
      stmtType?: string;
      block?: number;
      index?: number;
    }
  ): TargetDto {
    return {
      type,
      location: this.createLocation(fileName, className, methodName, options)
    };
  }

  /**
   * Convert a linear array of targets to a hierarchical tree node
   */
  static createLinearTree(targets: TargetDto[]): TargetTreeNodeDto | null {
    if (targets.length === 0) return null;

    let currentNode: TargetTreeNodeDto | undefined;

    for (let i = targets.length - 1; i >= 0; i--) {
      currentNode = {
        target: targets[i],
        children: currentNode ? [currentNode] : []
      };
    }

    return currentNode!;
  }

  /**
   * Flatten a target tree to get all targets
   */
  static flattenTargetTree(node: TargetTreeNodeDto): TargetDto[] {
    const targets: TargetDto[] = [node.target];

    for (const child of node.children) {
      targets.push(...this.flattenTargetTree(child));
    }

    return targets;
  }

  /**
   * Count total targets in a container
   */
  static countTargets(container: TargetsContainerDto): number {
    if (Array.isArray(container)) {
      return container.reduce((count, trace) => {
        if ('root' in trace) {
          return count + this.flattenTargetTree(trace.root).length;
        } else if ('targets' in trace) {
          return count + trace.targets.length;
        }
        return count;
      }, 0);
    } else if ('root' in container) {
      return this.flattenTargetTree(container.root).length;
    } else if ('targets' in container) {
      return container.targets.length;
    }
    return 0;
  }
}

/**
 * Pre-built target configurations for common scenarios
 */
export class TargetPresets {
  /**
   * Create a tree trace for a method execution flow
   */
  static methodExecutionTree(
    className: string,
    methodName: string,
    fileName: string,
    childMethods: Array<{
      className: string;
      methodName: string;
      fileName: string;
      type?: TargetTypeDto;
    }> = []
  ): TargetBuilder {
    const builder = new TargetBuilder();

    const children = childMethods.map(child => ({
      target: TargetUtils.createTarget(
        child.fileName,
        child.className,
        child.methodName,
        child.type || TargetTypeDto.INTERMEDIATE
      ),
      children: []
    }));

    const rootTarget = TargetUtils.createTarget(
      fileName,
      className,
      methodName,
      TargetTypeDto.INITIAL
    );

    builder.addTreeTrace(rootTarget, children);
    return builder;
  }

  /**
   * Create an execution path as a tree trace (linear chain)
   */
  static executionPath(targets: Array<{
    fileName: string;
    className: string;
    methodName: string;
    type?: TargetTypeDto;
    stmtType?: string;
  }>): TargetBuilder {
    const builder = new TargetBuilder();

    const targetDtos = targets.map((t, index) => TargetUtils.createTarget(
      t.fileName,
      t.className,
      t.methodName,
      t.type || (index === 0 ? TargetTypeDto.INITIAL :
                  index === targets.length - 1 ? TargetTypeDto.FINAL :
                  TargetTypeDto.INTERMEDIATE),
      { stmtType: t.stmtType }
    ));

    builder.addLinearAsTree(targetDtos);
    return builder;
  }

  /**
   * Create multiple method targets as separate tree traces
   */
  static multipleMethods(methods: Array<{
    fileName: string;
    className: string;
    methodName: string;
    type?: TargetTypeDto;
  }>): TargetBuilder {
    const builder = new TargetBuilder();

    methods.forEach(method => {
      builder.addMethodAsTree(
        method.className,
        method.methodName,
        method.fileName,
        { type: method.type }
      );
    });

    return builder;
  }
}
