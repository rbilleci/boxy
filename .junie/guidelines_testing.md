# Testing Guidelines for AI Code Agents

## Purpose

This document defines best practices for generating comprehensive, performant, resilient, and correct tests using compatible testing frameworks (e.g., JUnit, pytest, Mocha, etc.). These guidelines are **language-agnostic** and are designed for execution by AI code agents or assistants. All instructions below are to be interpreted and executed by AI code generation systems unless otherwise noted.

---

## 1. Completeness

* **Test All Code Paths:**
  Automatically generate and verify tests that cover every function, method, or module, including all edge cases and error conditions. If static or dynamic analysis detects uncovered paths, generate additional tests for those paths.

* **Positive & Negative Testing:**
  For each function or behavior, generate tests for both expected behavior (positive tests) and failure scenarios (negative tests), including invalid inputs and exceptions.

* **Boundary & Edge Cases:**
  Systematically generate tests for boundary values (e.g., empty, null, maximum/minimum values, zero) and any other edge cases inferred from the code or documentation.

* **Integration Tests:**
  Where applicable, generate and run integration tests to verify that components interact as intended. Identify and test interactions between modules or services.

* **Regression Tests:**
  If bug fixes or regressions are identified, generate or update tests to cover these scenarios and prevent recurrence. If provided, consult bug databases or commit logs.

* **Code Coverage:**
  Use automated code coverage tools to monitor coverage. If code coverage is below target thresholds, generate additional tests. Prefer meaningful tests to maximize useful coverage.

---

## 2. Performance

* **Fast Tests:**
  Generate tests that complete execution rapidly. Minimize or eliminate any unnecessary waiting, sleeping, or dependence on slow external resources.

* **Isolated Tests:**
  Ensure each test is independent. Avoid shared mutable state between tests. Do not rely on persistent external systems (databases, file systems, networks) unless the test is specifically for integration.

* **Resource Management:**
  Automatically ensure proper cleanup of all resources (files, connections, threads, memory) after each test to prevent leaks and test flakiness.

* **Scalable Test Suites:**
  Generate test suites to support parallel or distributed execution if the framework allows. Avoid global state that would prevent parallel execution.

---

## 3. Resilience

* **Deterministic Results:**
  Ensure generated tests produce consistent results on repeated runs, regardless of order or environment, unless testing for randomness is explicit.

* **Mock External Systems:**
  Use mocks, stubs, or fakes for external dependencies (network, files, services) to ensure test reliability. Detect and mock external dependencies where possible.

* **Timeouts:**
  Assign reasonable timeouts to tests and long-running operations to avoid indefinite hangs.

* **Error Handling:**
  Generate assertions that specifically check for correct error messages, types, or codes.

* **Retry Logic (if justified):**
  Only implement retries for genuinely flaky tests (e.g., integration tests involving unstable services). Always log and document the reason for any retry logic.

---

## 4. Correctness

* **Clear Assertions:**
  Use explicit, descriptive assertions. Ensure each test checks a single, clearly defined behavior or outcome.

* **Single Responsibility:**
  Each test must focus on one behavior or scenario. Generate multiple tests for multiple scenarios, not one test covering all.

* **Test Naming:**
  Name tests to clearly describe their intent and scenario (e.g., `shouldReturnEmptyListWhenNoItems`). Use language-appropriate conventions for test naming.

* **Setup & Teardown:**
  Use appropriate setup/teardown hooks (e.g., fixtures, `beforeEach`, `afterEach`) to prepare and clean up test environments. Auto-generate these sections when needed.

* **Avoid Hardcoding:**
  Use constants, fixtures, or data builders to generate test values. Avoid hardcoding values directly in test code.

* **Documentation:**
  Add comments to generated tests when logic is non-obvious or when covering specific edge or regression scenarios.

---

## 5. General Practices

* **Keep Tests Updated:**
  Automatically update or regenerate tests when code, requirements, or interfaces change.

* **Automate Test Execution:**
  Ensure all tests are integrated with the CI/CD pipeline and are executed automatically on code changes.

* **Review Tests Like Code:**
  Treat generated test code with the same standards for quality, readability, and maintainability as production code.

---

## 6. AI Code Agents

* **Test Your Changes:**
  After code generation or modification, always run relevant tests locally or in the configured environment. Attempt test runs at least 3 times to ensure all assertions pass and to rule out flakiness. If failures occur, automatically diagnose and fix them before proceeding.

* **Self-Evaluation:**
  Whenever possible, analyze test outcomes and coverage reports to determine if additional tests are required for completeness, correctness, or performance. Generate additional tests as needed until all criteria are satisfied.

---

## 7. Checklist

* [ ] Every function/method has tests for typical and edge cases.
* [ ] All assertions are clear and descriptive.
* [ ] No test depends on another test’s outcome.
* [ ] No external system is required unless integration is intended.
* [ ] Tests are fast and reliable.
* [ ] Test names reflect their purpose.
* [ ] Code coverage is monitored and improved over time.
* [ ] Tests are included in CI/CD.
* [ ] All tests have been executed at least 3 times, with consistent results.
