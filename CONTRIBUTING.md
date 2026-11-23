# Contributing to STREAMX

Thank you for your interest in contributing to **STREAMX**! We welcome bug reports, feature suggestions, architectural improvements, and code contributions.

---

## 📋 Code of Conduct

- Be respectful, constructive, and collaborative.
- Provide clear context and reproduction steps when reporting issues.
- Focus on code quality, performance, and keeping the iOS-inspired Liquid Glass design language clean and consistent.

---

## 🛠️ Development Workflow

1. **Fork & Branch**:
   - Fork the repository on GitHub.
   - Clone your fork locally:
     ```bash
     git clone https://github.com/<your-username>/StreamX.git
     cd StreamX
     ```
   - Create a feature or bugfix branch off `main`:
     ```bash
     git checkout -b feature/amazing-feature
     ```

2. **Coding Standards & Architecture**:
   - Follow standard **Kotlin Coding Conventions** and Jetpack Compose best practices.
   - Maintain the **Clean Architecture** boundaries:
     - Presentation (`presentation/`) -> ViewModels (`presentation/`) -> Domain (`domain/`) -> Data (`data/`, `network/`).
   - Use Compose Material 3 and theme tokens from `presentation/theme/`.
   - Never hardcode sensitive API keys, machine paths, or personal user data in source code.
   - Keep user privacy first: do not add unapproved tracking, analytics SDKs, or cloud auth mandates without community discussion.

3. **Testing**:
   - Write unit tests for new business logic, parsers, or repositories under `app/src/test/`.
   - Ensure all existing unit tests pass before submitting your PR:
     ```bash
     ./gradlew testDebugUnitTest
     ```

4. **Submitting a Pull Request**:
   - Push your branch to your fork:
     ```bash
     git push origin feature/amazing-feature
     ```
   - Open a Pull Request against `IconicAashu/StreamX:main`.
   - Provide a clear PR description detailing:
     - The problem being solved.
     - Screenshots / screen recordings (especially for UI or Liquid Glass changes).
     - Test verification steps.

---

## 📬 Need Help?

If you encounter issues or have questions regarding architecture or add-on integration:
- Open an [Issue on GitHub](https://github.com/IconicAashu/StreamX/issues).
- Reach out to the maintainer at [aashu@hilmail.co](mailto:aashu@hilmail.co).
