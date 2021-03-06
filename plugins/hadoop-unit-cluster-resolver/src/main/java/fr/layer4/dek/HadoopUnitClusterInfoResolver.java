package fr.layer4.dek;

/*-
 * #%L
 * DEK
 * %%
 * Copyright (C) 2018 Layer4
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import fr.layer4.dek.auth.Credentials;
import fr.layer4.dek.cluster.Cluster;
import fr.layer4.dek.info.ClusterInfoResolver;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class HadoopUnitClusterInfoResolver implements ClusterInfoResolver {

    private final Properties hadoopUnitProperties;

    @Override
    public String getType() {
        return "hadoop-unit";
    }

    @Override
    public Collection<ServiceClientAndVersion> resolveAvailableServiceClients(Cluster cluster) {
        URI uri = cluster.getUri();

        Path hadoopPropertiesPath = Paths.get(uri).resolve(Paths.get("conf", "hadoop.properties"));
        Path hadoopUnitDefaultPropertiesPath = Paths.get(uri).resolve(Paths.get("conf", "hadoop-unit-default.properties"));

        // Check hadoop.properties and hadoop-unit-default.properties
        if (!hadoopPropertiesPath.toFile().exists()) {
            throw new DekException("Can not find hadoop.properties at :" + hadoopPropertiesPath.toString());
        }
        if (!hadoopUnitDefaultPropertiesPath.toFile().exists()) {
            throw new DekException("Can not find hadoop-unit-default.properties at :" + hadoopUnitDefaultPropertiesPath.toString());
        }

        // Load hadoop.properties in order to find services
        Properties services = new Properties();
        try {
            services.load(Files.newBufferedReader(hadoopPropertiesPath));
        } catch (IOException e) {
            throw new DekException(e);
        }

        // Load hadoop-unit-default.properties in order to match version
        Properties config = new Properties();
        try {
            config.load(Files.newBufferedReader(hadoopUnitDefaultPropertiesPath));
        } catch (IOException e) {
            throw new DekException(e);
        }

        return services.entrySet().stream()
                .filter(s -> Boolean.valueOf((String) s.getValue()) && config.containsKey(s.getKey() + ".artifact"))
                .map(s -> {
                    String service = ((String) s.getKey()).toLowerCase();
                    if ("hiveserver2".equalsIgnoreCase(service)) {
                        service = "hive";
                    }
                    return ServiceClientAndVersion.of(
                            service,
                            ((String) config.get(s.getKey() + ".artifact")).replace("fr.jetoile.hadoop:hadoop-unit-" + s.getKey() + ":", ""));
                })
                .map(s -> {
                    // Find HDP version for this HU version
                    String hdpVersion = this.hadoopUnitProperties.getProperty("hadoop-unit[" + s.getVersion() + "]", this.hadoopUnitProperties.getProperty("hadoop-unit.default"));

                    // Find service version for this HDP version
                    String serviceVersion = this.hadoopUnitProperties.getProperty("hdp." + hdpVersion + "." + s.getService(), this.hadoopUnitProperties.getProperty("hdp.default." + s.getService()));

                    return ServiceClientAndVersion.of(s.getService(), serviceVersion);
                })
                .filter(s -> s != null && StringUtils.isNotBlank(s.getVersion()))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, List<String>> resolveEnvironmentVariables(Path archivesPath, Path clusterGeneratedPath, Cluster cluster) {
        Collection<ServiceClientAndVersion> serviceClientAndVersions = resolveAvailableServiceClients(cluster);
        return resolveEnvironmentVariablesFromServices(clusterGeneratedPath, serviceClientAndVersions, cluster);
    }

    protected Map<String, List<String>> resolveEnvironmentVariablesFromServices(Path clusterGeneratedPath, Collection<ServiceClientAndVersion> serviceClientAndVersions, Cluster cluster) {
        Map<String, List<String>> envVars = new HashMap<>();

        Properties config = loadConfig(cluster);

        // Get services and components
        // For each components, add custom entries in envVars
        serviceClientAndVersions.forEach(e -> {
            switch (e.getService().toLowerCase()) {
                case DefaultServices.HDFS:
                    envVars.put("HADOOP_CONF_DIR", Collections.singletonList(clusterGeneratedPath.resolve(e.getService()).toAbsolutePath().toString()));
                    envVars.put("HADOOP_CLIENT_OPTS", Collections.singletonList("-Xmx1g"));
                    envVars.put("MAPRED_DISTCP_OPTS", Collections.singletonList("-Xmx2g"));
                    envVars.put("HADOOP_DISTCP_OPTS", Collections.singletonList("-Xmx2g"));
                    break;
                case DefaultServices.HBASE:
                    envVars.put("HBASE_CONF_DIR", Collections.singletonList(clusterGeneratedPath.resolve(e.getService()).toAbsolutePath().toString()));
                    break;
                case DefaultServices.YARN:
                    envVars.put("YARN_CONF_DIR", Collections.singletonList(clusterGeneratedPath.resolve(e.getService()).toAbsolutePath().toString()));
                    break;
                case DefaultServices.ZOOKEEPER:
                    envVars.put("ZOOKEEPER_CONF_DIR", Collections.singletonList(clusterGeneratedPath.resolve(e.getService()).toAbsolutePath().toString()));
                    break;
                case DefaultServices.OOZIE:
                    envVars.put("OOZIE_URL", Collections.singletonList("http://" + config.getProperty("oozie.host") + ":" + config.getProperty("oozie.port") + "/oozie"));
                    break;
                case DefaultServices.PIG:
                    break;
                case DefaultServices.HIVE:
                    break;
                default:
                    break;
            }
        });
        return envVars;
    }

    protected Properties loadConfig(Cluster cluster) {
        Properties config = new Properties();
        try {
            config.load(Files.newBufferedReader(Paths.get(cluster.getUri()).resolve(Paths.get("conf", "hadoop-unit-default.properties"))));
        } catch (IOException e) {
            throw new DekException(e);
        }
        return config;
    }

    @Override
    public Map<String, Map<String, byte[]>> renderConfigurationFiles(Cluster cluster) {
        Collection<ServiceClientAndVersion> serviceClientAndVersions = resolveAvailableServiceClients(cluster);

        Properties config = loadConfig(cluster);

        return serviceClientAndVersions.stream().map(sv -> {
            Map<String, byte[]> files = new HashMap<>();
            switch (sv.getService()) {
                case DefaultServices.HDFS:
                    Map<String, String> coreSite = new TreeMap<>();
                    coreSite.put("fs.defaultFS", "hdfs://" + config.getProperty("hdfs.namenode.host") + ":" + config.getProperty("hdfs.namenode.port"));
                    files.put("core-site.xml", renderXmlConfiguration(coreSite));
                    Map<String, String> hdfsSite = new TreeMap<>();
                    files.put("hdfs-site.xml", renderXmlConfiguration(hdfsSite));
                    break;
                case DefaultServices.HBASE:
                    Map<String, String> hbaseSite = new TreeMap<>();
                    hbaseSite.put("hbase.zookeeper.quorum", config.getProperty("zookeeper.host", "127.0.0.1"));
                    hbaseSite.put("hbase.zookeeper.client.port", config.getProperty("zookeeper.port", "22010"));
                    hbaseSite.put("zookeeper.znode.parent", config.getProperty("hbase.znode.parent", "/hbase-unsecure"));
                    files.put("hbase-site.xml", renderXmlConfiguration(hbaseSite));
                    break;
                case DefaultServices.HIVE:
                    Map<String, String> hiveSite = new TreeMap<>();
                    hiveSite.put("hive.metastore.uris", "thrift://" + config.getProperty("hive.metastore.hostname", "127.0.0.1") + ":" + config.getProperty("hive.metastore.port", "20102"));
                    files.put("hive-site.xml", renderXmlConfiguration(hiveSite));
                    break;
                default:
                    break;
            }
            return Pair.of(sv.getService(), files);
        }).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    @Override
    public Credentials getCredentials() {
        return null;
    }

    protected byte[] renderXmlConfiguration(Map<String, String> config) {
        return ("<configuration>\r\n"
                + config.entrySet()
                .stream()
                .map(e -> "    <property>\r\n        <name>" + e.getKey() + "</name>\r\n        <value>" + e.getValue() + "</value>\r\n    </property>\r\n")
                .collect(Collectors.joining())
                + "</configuration>").getBytes();
    }
}
