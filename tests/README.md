# Regression tests

The suites load the production Groovy scripts with lightweight Hubitat mocks.
They exercise commands, callbacks, scheduling, parsers, and state transitions
without contacting devices or cloud APIs. They do not replace deployment testing
on a Hubitat hub or validate firmware-specific behavior.

Prerequisites: Java 8 or later and the Groovy 2.4.21 standalone runtime. Download
the runtime to a temporary cache (not the repository):

```powershell
$cache = Join-Path $env:TEMP 'hubitat-driver-tests'
New-Item -ItemType Directory -Path $cache -Force | Out-Null
curl.exe --fail --location 'https://repo.maven.apache.org/maven2/org/codehaus/groovy/groovy-all/2.4.21/groovy-all-2.4.21.jar' --output (Join-Path $cache 'groovy-all-2.4.21.jar')
.\tests\Run-Tests.ps1
```

Use `-GroovyJar <absolute-path>` for an existing runtime or `-Suite apps` to run
only the app tests. Each `*Test.groovy` entry point must run from the repository
root. A failed assertion or compilation error fails the runner.