"use strict";

exports.invoke = (argumentsList) => argumentsList;

exports.invokeCase = ({ receiver, arguments: argumentsList }) => ({ receiver, arguments: argumentsList });

exports.materializeConstructorPlan = (reference, argumentsList) => ({
  constructedBy: `${reference.modulePath}#${reference.exportName}`,
  label: argumentsList[0],
});

exports.materializeCallable = (reference) => {
  if (reference.modulePath === "fixtures/functions.cjs" && reference.exportName === "predicate") {
    return (value) => Boolean(value);
  }
  throw new Error(`unknown fixture callable ${reference.modulePath}#${reference.exportName}`);
};
