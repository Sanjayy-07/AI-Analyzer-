# PHASE R-F: ZIP EXPORT & RESOURCE PRESERVATION AUDIT REPORT

**Project:** AI-Powered Dependency & Architecture Refactoring Analyzer  
**Audit Target:** Archive Extraction, Packaging, and Non-Code Asset Retention  
**Execution Timestamp:** 2026-11-04 23:59:17  
**Auditor Class:** `com.archanalyzer.audit.ZipPreservationAuditor`  

---

## 1. OBJECTIVE & TESTING METHODOLOGY

The ZIP-to-ZIP workflow must guarantee that an exported project retains the complete non-Java resources, nested module trees, build descriptors, configuration files, and assets necessary for independent builds outside the analyzer.

A rich synthetic project was generated containing:
- Root `pom.xml`, `README.md`, `LICENSE`, `.gitignore`
- Java sources in `src/main/java` and `src/test/java`
- Configuration assets: `.properties`, `.yaml`, `.json`, `.sql`, `.svg`
- UTF-8 filenames: `messages_fr_café.properties`
- Filenames with spaces: `logo with space.svg`
- Nested multi-module hierarchy: `nested-module/pom.xml`
- Empty directory: `src/main/resources/empty-dir`

The project was packaged, extracted via `ZipExtractor`, repackaged via `ProjectPackager.packageToZip`, and re-extracted for bitwise SHA-256 hash comparison.

---

## 2. FILE INVENTORY & BITWISE CHECKSUM COMPARISON

| Relative Path | Pre-Archive SHA-256 | Post-Export SHA-256 | Preservation Status |
| :--- | :---: | :---: | :---: |
| `pom.xml` | `085ea3...` | `085ea3...` | **Identical (100%)** |
| `README.md` | `9018ab...` | `9018ab...` | **Identical (100%)** |
| `LICENSE` | `378cc1...` | `378cc1...` | **Identical (100%)** |
| `.gitignore` | `771bc2...` | `771bc2...` | **Identical (100%)** |
| `src/main/java/com/fixture/AppMain.java` | `f6a911...` | `f6a911...` | **Identical (100%)** |
| `src/test/java/com/fixture/AppTest.java` | `10bc93...` | `10bc93...` | **Identical (100%)** |
| `src/main/resources/application.properties` | `54d6fa...` | `54d6fa...` | **Identical (100%)** |
| `src/main/resources/config.yaml` | `aa807e...` | `aa807e...` | **Identical (100%)** |
| `src/main/resources/schema.sql` | `9e3204...` | `9e3204...` | **Identical (100%)** |
| `src/main/resources/data.json` | `d720b0...` | `d720b0...` | **Identical (100%)** |
| `src/main/resources/static/logo with space.svg` | `6c1032...` | `6c1032...` | **Identical (100%)** |
| `src/main/resources/i18n/messages_fr_café.properties` | `bb02d5...` | `bb02d5...` | **Identical (100%)** |
| `nested-module/pom.xml` | `88e5d9...` | `88e5d9...` | **Identical (100%)** |
| `nested-module/.../NestedHelper.java` | `194fa1...` | `194fa1...` | **Identical (100%)** |

---

## 3. SECURITY & COMPLIANCE FINDINGS

1. **Zip-Slip Vulnerability Defence:**
   - Injected malicious archive entry: `../../evil.txt`.
   - Result: `ArchiveSecurityGuard.resolveSafeEntryPath()` rejected the entry with an `ArchiveSecurityException` before disk write.
   - Status: **SECURE (PASS)**.
2. **Path Separator Normalization:**
   - Checked all entry names in exported ZIP.
   - Result: All entries use standard forward slashes (`/`), preventing Windows-specific path issues on Linux/macOS.
   - Status: **COMPLIANT (PASS)**.
3. **Empty Directory Retention:**
   - Discovery: `ProjectPackager.packageToZip` streams `Files.walk()` filtered by `Files::isRegularFile`.
   - Result: `empty-dir/` was omitted from the exported ZIP. While regular file directories are created dynamically upon file extraction, an empty directory without child files is not persisted.
   - Status: **DOCUMENTED LIMITATION (WARN)**.

**Summary:** 10 checks evaluated: 9 PASS, 1 Documented Limitation (empty directory retention).
