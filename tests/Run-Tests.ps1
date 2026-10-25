param(
	[string]$GroovyJar = (Join-Path $env:TEMP 'hubitat-driver-tests\groovy-all-2.4.21.jar'),
	[string]$Suite = '*'
)

$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $GroovyJar -PathType Leaf)) {
	throw 'Groovy 2.4.21 is required. See tests\README.md or supply -GroovyJar.'
}
$GroovyJar = (Resolve-Path -LiteralPath $GroovyJar).Path
$suitePath = Join-Path (Join-Path $PSScriptRoot $Suite) '*'
$tests = @(Get-ChildItem -LiteralPath $PSScriptRoot -Recurse -Filter '*Test.groovy' -File |
	Where-Object { $_.FullName -like $suitePath } | Sort-Object FullName)
if ($tests.Count -eq 0) {
	throw "No test suites match '$Suite'."
}

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
	foreach ($test in $tests) {
		Write-Host "Running $($test.Name)"
		& java '-Duser.timezone=UTC' -cp $GroovyJar groovy.ui.GroovyMain $test.FullName
		if ($LASTEXITCODE -ne 0) {
			throw "$($test.Name) failed with exit code $LASTEXITCODE."
		}
	}
	Write-Host "Passed $($tests.Count) Groovy test suites."
} finally {
	Pop-Location
}