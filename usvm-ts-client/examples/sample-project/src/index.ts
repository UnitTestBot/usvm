// Main entry point for the sample project
// This file provides clear entry points that USVM can analyze

export * from './main';
export * from './auth';
export * from './calculator';

// Re-export main classes and functions for easy access
export { Application, main } from './main';
export { AuthService, User } from './auth';
export { Calculator, processNumbers } from './calculator';

// Default export - main application entry point
import { main } from './main';
export default main;
