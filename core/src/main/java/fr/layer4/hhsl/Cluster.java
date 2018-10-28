package fr.layer4.hhsl;

import lombok.Data;

import java.net.URI;
import java.util.Map;

@Data
public class Cluster {
    private Long id;
    private String registry;
    private String name;
    private String type;
    private String user;
    private String password;
    private byte[] banner;
    private URI uri;
    private Map<String, String> metadata;
}
