// @ts-nocheck

export { default as RenamedDefault } from './defaultExport';

const actualDefault = 42;
export { actualDefault as default };
