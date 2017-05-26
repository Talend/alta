# Alta Maven Plugin

This a fork of https://github.com/veithen/alta repository. We made it to extend supported properties in name/value templates (see http://veithen.github.io/alta/properties.html):

1. **mavenPath** - classpath for UNIX
2. **mavenPathWindows** - classpath for Windows

##### Examples

mavenPath: `org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar:org/apache/commons/commons-lang3/3.4/commons-lang3-3.4-sources.jar`

mavenPathWindows `org\apache\commons\commons-lang3\3.4\commons-lang3-3.4.jar;org\apache\commons\commons-lang3\3.4\commons-lang3-3.4-sources.jar`

##### Usage

This is used in [components-api-service-rest](https://github.com/Talend/components/tree/master/services/components-api-service-rest) and [components-api-service-rest-all-components](https://github.com/Talend/components/tree/master/services/components-api-service-rest-all-components) to generate classpath which is pasted in service start-up scripts