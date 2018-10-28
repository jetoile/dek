package fr.layer4.hhsl.commands;

import fr.layer4.hhsl.Cluster;
import fr.layer4.hhsl.Constants;
import fr.layer4.hhsl.binaries.BinariesStore;
import fr.layer4.hhsl.registry.Registry;
import fr.layer4.hhsl.registry.RegistryManager;
import fr.layer4.hhsl.info.ClusterInfoManager;
import fr.layer4.hhsl.info.ClusterInfoResolver;
import fr.layer4.hhsl.store.Store;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;
import org.springframework.shell.table.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.springframework.shell.table.CellMatchers.at;

@Slf4j
@ShellComponent
public class ClusterCommands {

    @Autowired
    private Store store;

    @Autowired
    private RegistryManager registryManager;

    @Autowired
    private ClusterInfoManager clusterInfoManager;

    @Autowired
    private BinariesStore binariesStore;

    @ShellMethodAvailability(value = "*")
    public Availability availabilityAfterUnlock() {
        return Avaibilities.unlockedAndReady(store);
    }

    @ShellMethod(key = "list cluster", value = "List all clusters", group = "Cluster")
    public Table listClusters(@ShellOption(defaultValue = Constants.LOCAL_REGISTRY_NAME, value = "registry") String registryName) {
        Registry registry = registryManager.getFromName(registryName);

        List<Cluster> clusters = registry.getClusterService().listClusters();

        String[][] data = new String[clusters.size() + 1][];
        data[0] = new String[]{"Name", "Type", "URI"};

        int it = 1;
        Iterator<Cluster> iterator = clusters.iterator();
        while (iterator.hasNext()) {
            Cluster next = iterator.next();
            data[it] = new String[]{next.getName(), next.getType(), next.getUri().toString()};
            it++;
        }
        TableModel model = new ArrayTableModel(data);
        TableBuilder tableBuilder = new TableBuilder(model);
        tableBuilder.addHeaderBorder(BorderStyle.fancy_double);

        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                tableBuilder.on(at(i, j)).addAligner(SimpleHorizontalAligner.left).addSizer(new AbsoluteWidthSizeConstraints(5));
                tableBuilder.on(at(i, j)).addAligner(SimpleVerticalAligner.middle);
            }
        }

        return tableBuilder.build();
    }

    @ShellMethod(key = "delete cluster", value = "Delete a cluster inside a registry", group = "Cluster")
    public void deleteCluster(@ShellOption(defaultValue = Constants.LOCAL_REGISTRY_NAME) String registryName, String name) {
        Registry registry = registryManager.getFromName(registryName);
        registry.getClusterService().deleteCluster(name);
    }

    @ShellMethod(key = "add cluster", value = "Add a cluster", group = "Cluster")
    public void addCluster(
            @ShellOption(defaultValue = Constants.LOCAL_REGISTRY_NAME) String registryName,
            String type,
            String name,
            String url,
            @ShellOption(defaultValue = Constants.DEFAULT_BANNER) String banner) {
        Registry registry = registryManager.getFromName(registryName);
        registry.getClusterService().addCluster(type, name, url, banner);

        // Download missing clients
        // TODO Use Aether to donwload clients as Maven dependency?
        binariesStore.prepare("", "");
    }

    @ShellMethod(key = {"use", "use cluster"}, value = "Use the configuration of a cluster", group = "Cluster")
    public void useCluster(@ShellOption(defaultValue = Constants.LOCAL_REGISTRY_NAME) String registryName, String name) {
        Registry registry = registryManager.getFromName(registryName);
        Cluster cluster = registry.getClusterService().getCluster(name);

//        SpelCompiler. // TODO
        byte[] banner = cluster.getBanner();

        // Print banner
        // TODO

        ClusterInfoResolver clusterInfoResolver = clusterInfoManager.fromType(cluster.getType());

        // Print and change env variables for current session
        Map<String, String> env = clusterInfoResolver.resolveEnvironmentVariables(cluster);
        // TODO

        // Render configuration files
        Map<String, byte[]> files = clusterInfoResolver.renderConfigurationFiles(cluster);
        // TODO
    }
}
