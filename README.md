# Universal Symbolic Virtual Machine

**Universal Symbolic Virtual Machine**, or **USVM**, is an ultimately powerful _language-agnostic_ core for implementing
custom symbolic execution based products.

## How we got here

USVM is the result of years of experience designing symbolic execution engines for various programming languages. Many
engines have a lot in common — so why don't we extract and polish it? Many of them still feature different advantages
— so why don't we unify them to get the most out of symbolic execution?

USVM abstracts language primitives into the generic ones, so, with a simple DSL provided, you are free to implement an
interpreter for your language. USVM integrates the particular symbolic execution enhancements into one compact form
— an
efficient and configurable
core with a unified API.

## Key features

What does it mean to be an _efficient_ core?

The USVM core redefines language primitives, so they become symbolic and language-independent, and provides us
with a range of benefits:
* optimized constraint management to reduce SMT solver workload;
* extensible symbolic memory model;
* forward and backward symbolic exploration;
* improved symbolic models for containers (`map`, `set`, `list`, etc.);
* solving type constraints with no SMT solver involved;
* bit-precise reasoning.

## Language-specific implementations

We are now developing a user-friendly USVM API, so that you can easily adapt the core to analyzing programs
in the required language.

For now, we have already implemented symbolic interpreters for _Java_ and _Python_ (the latter is an experimental but
working example). And the interpreters for _Go_ and _JavaScript_ are under development, so stay tuned.

These ready-to-use symbolic interpreters are for the managed languages mostly, but you have everything you need to
analyze programs in whatever language — whether it is _C++_ or an exotic (or even custom) one.

## Applicable scope

Symbolic execution is known to be a powerful but slow and demanding technique. USVM makes it faster, more stable, and
versatile.

You can build custom symbolic execution engines with the USVM core inside to show better performance in
* test generation,
* static analysis,
* verification
* targeted fuzzing,</br>and more symbolic execution based solutions.
