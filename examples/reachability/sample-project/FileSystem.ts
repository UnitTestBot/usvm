// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

// File System Simulator - Sample TypeScript project for reachability analysis
// This project simulates basic file system operations without floating-point arithmetic

export class FileSystemNode {
    public name: string;
    public isDirectory: boolean;
    public parent: FileSystemNode | null;
    public children: FileSystemNode[];
    public content: string;
    public permissions: number; // Using integer permissions (e.g., 755, 644)
    public size: number;

    constructor(name: string, isDirectory: boolean = false, parent: FileSystemNode | null = null) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.parent = parent;
        this.children = [];
        this.content = "";
        this.permissions = isDirectory ? 755 : 644;
        this.size = 0;
    }

    public addChild(child: FileSystemNode): boolean {
        if (!this.isDirectory) {
            throw new Error("Cannot add child to a file");
        }

        // Check if child already exists
        for (let i = 0; i < this.children.length; i++) {
            if (this.children[i].name === child.name) {
                return false; // Child already exists
            }
        }

        child.parent = this;
        this.children.push(child);
        return true;
    }

    public removeChild(name: string): boolean {
        if (!this.isDirectory) {
            return false;
        }

        for (let i = 0; i < this.children.length; i++) {
            if (this.children[i].name === name) {
                this.children.splice(i, 1);
                return true;
            }
        }
        return false;
    }

    public findChild(name: string): FileSystemNode | null {
        if (!this.isDirectory) {
            return null;
        }

        for (let i = 0; i < this.children.length; i++) {
            if (this.children[i].name === name) {
                return this.children[i];
            }
        }
        return null;
    }

    public getPath(): string {
        if (this.parent === null) {
            return "/";
        }

        let path = "";
        let current: FileSystemNode | null = this;

        while (current !== null && current.parent !== null) {
            path = "/" + current.name + path;
            current = current.parent;
        }

        return path === "" ? "/" : path;
    }

    public calculateSize(): number {
        if (!this.isDirectory) {
            return this.content.length;
        }

        let totalSize = 0;
        for (let i = 0; i < this.children.length; i++) {
            totalSize += this.children[i].calculateSize();
        }
        this.size = totalSize;
        return totalSize;
    }
}

export class FileSystem {
    private root: FileSystemNode;
    private currentDirectory: FileSystemNode;

    constructor() {
        this.root = new FileSystemNode("", true);
        this.currentDirectory = this.root;
    }

    public createFile(path: string, content: string = ""): boolean {
        const pathParts = this.parsePath(path);
        if (pathParts.length === 0) {
            return false;
        }

        const fileName = pathParts[pathParts.length - 1];
        const dirPath = pathParts.slice(0, -1);
        const parentDir = this.navigateToPath(dirPath);

        if (parentDir === null || !parentDir.isDirectory) {
            return false;
        }

        const newFile = new FileSystemNode(fileName, false, parentDir);
        newFile.content = content;
        newFile.size = content.length;

        return parentDir.addChild(newFile);
    }

    public createDirectory(path: string): boolean {
        const pathParts = this.parsePath(path);
        if (pathParts.length === 0) {
            return false;
        }

        const dirName = pathParts[pathParts.length - 1];
        const parentPath = pathParts.slice(0, -1);
        const parentDir = this.navigateToPath(parentPath);

        if (parentDir === null || !parentDir.isDirectory) {
            return false;
        }

        const newDir = new FileSystemNode(dirName, true, parentDir);
        return parentDir.addChild(newDir);
    }

    public deleteFile(path: string): boolean {
        const pathParts = this.parsePath(path);
        if (pathParts.length === 0) {
            return false;
        }

        const fileName = pathParts[pathParts.length - 1];
        const dirPath = pathParts.slice(0, -1);
        const parentDir = this.navigateToPath(dirPath);

        if (parentDir === null) {
            return false;
        }

        const fileToDelete = parentDir.findChild(fileName);
        if (fileToDelete === null || fileToDelete.isDirectory) {
            return false;
        }

        return parentDir.removeChild(fileName);
    }

    public readFile(path: string): string | null {
        const node = this.findNode(path);
        if (node === null || node.isDirectory) {
            return null;
        }
        return node.content;
    }

    public writeFile(path: string, content: string): boolean {
        const node = this.findNode(path);
        if (node === null || node.isDirectory) {
            return false;
        }

        node.content = content;
        node.size = content.length;
        return true;
    }

    public listDirectory(path: string = ""): string[] {
        const node = path === "" ? this.currentDirectory : this.findNode(path);
        if (node === null || !node.isDirectory) {
            return [];
        }

        const result: string[] = [];
        for (let i = 0; i < node.children.length; i++) {
            const child = node.children[i];
            const prefix = child.isDirectory ? "d " : "f ";
            result.push(prefix + child.name);
        }
        return result;
    }

    public changeDirectory(path: string): boolean {
        const node = this.findNode(path);
        if (node === null || !node.isDirectory) {
            return false;
        }

        this.currentDirectory = node;
        return true;
    }

    public getCurrentPath(): string {
        return this.currentDirectory.getPath();
    }

    public copyFile(sourcePath: string, destPath: string): boolean {
        const sourceNode = this.findNode(sourcePath);
        if (sourceNode === null || sourceNode.isDirectory) {
            return false;
        }

        return this.createFile(destPath, sourceNode.content);
    }

    public moveFile(sourcePath: string, destPath: string): boolean {
        const sourceNode = this.findNode(sourcePath);
        if (sourceNode === null || sourceNode.isDirectory) {
            return false;
        }

        const content = sourceNode.content;
        if (!this.deleteFile(sourcePath)) {
            return false;
        }

        if (!this.createFile(destPath, content)) {
            // Restore the original file if destination creation fails
            this.createFile(sourcePath, content);
            return false;
        }

        return true;
    }

    public findFiles(pattern: string, startPath: string = ""): string[] {
        const startNode = startPath === "" ? this.root : this.findNode(startPath);
        if (startNode === null) {
            return [];
        }

        const results: string[] = [];
        this.searchRecursive(startNode, pattern, results);
        return results;
    }

    private searchRecursive(node: FileSystemNode, pattern: string, results: string[]): void {
        if (!node.isDirectory && node.name.indexOf(pattern) >= 0) {
            results.push(node.getPath());
        }

        if (node.isDirectory) {
            for (let i = 0; i < node.children.length; i++) {
                this.searchRecursive(node.children[i], pattern, results);
            }
        }
    }

    private parsePath(path: string): string[] {
        if (path === "" || path === "/") {
            return [];
        }

        // Remove leading slash and split
        const cleaned = path.startsWith("/") ? path.substring(1) : path;
        return cleaned.split("/").filter(part => part.length > 0);
    }

    private navigateToPath(pathParts: string[]): FileSystemNode | null {
        let current = this.root;

        for (let i = 0; i < pathParts.length; i++) {
            const part = pathParts[i];
            if (part === "..") {
                if (current.parent !== null) {
                    current = current.parent;
                }
            } else if (part !== ".") {
                const child = current.findChild(part);
                if (child === null || !child.isDirectory) {
                    return null;
                }
                current = child;
            }
        }

        return current;
    }

    private findNode(path: string): FileSystemNode | null {
        const pathParts = this.parsePath(path);
        if (pathParts.length === 0) {
            return this.root;
        }

        const fileName = pathParts[pathParts.length - 1];
        const dirPath = pathParts.slice(0, -1);
        const parentDir = this.navigateToPath(dirPath);

        if (parentDir === null) {
            return null;
        }

        return parentDir.findChild(fileName);
    }
}
