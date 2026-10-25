import groovy.json.JsonSlurper

File root = new File('.').canonicalFile
def parser = new JsonSlurper()
Map bundle = parser.parse(new File(root, 'packageManifest.json')) as Map
Map repository = parser.parse(new File(root, 'repository.json')) as Map
String rawPrefix = 'https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/'
assert repository.packages.any { it.location == rawPrefix + 'packageManifest.json' }
List entries = (bundle.drivers ?: []) + (bundle.apps ?: [])
assert entries*.id.unique().size() == entries.size(): 'Bundle component UUIDs must be unique'

def sourceVersion = { File source ->
	String text = source.getText('UTF-8')
	def constant = text =~ /(?m)@Field\s+static\s+final\s+String\s+(?:DRIVER_VERSION|APP_VERSION|VERSION)\s*=\s*["'](\d+\.\d+\.\d+)["']/
	if (constant.find()) { return constant.group(1) }
	def header = text =~ /(?m)^\s*\*\s*Version:\s*(\d+\.\d+\.\d+)/
	assert header.find(): "No source version found for ${source}"
	header.group(1)
}

entries.each { entry ->
	UUID.fromString(entry.id as String)
	assert entry.location.startsWith(rawPrefix)
	File source = new File(root, entry.location.substring(rawPrefix.length()))
	assert source.isFile(): "Missing source for ${entry.name}"
	assert entry.version == sourceVersion(source): "Bundle/source version mismatch for ${entry.name}"
}

int manifests = 2
['apps', 'drivers'].each { folder ->
	new File(root, folder).eachFileRecurse { File file ->
		if (file.name == 'packageManifest.json') {
			manifests++
			Map component = parser.parse(file) as Map
			assert component.version
			((component.drivers ?: []) + (component.apps ?: [])).each { entry ->
				Map bundled = entries.find { it.location == entry.location }
				assert bundled: "Component omitted from bundle: ${entry.name}"
				['id', 'name', 'namespace', 'version'].each { field ->
					assert bundled[field] == entry[field]: "${entry.name}: ${field} differs between manifests"
				}
			}
		} else if (file.name.endsWith('.groovy')) {
			String relative = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
			assert entries.any { it.location == rawPrefix + relative }: "Unpackaged source: ${relative}"
		}
	}
}
println "Manifest regressions passed (${manifests} JSON files, ${entries.size()} components, source versions, UUIDs, coverage, discovery)."