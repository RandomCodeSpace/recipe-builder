package com.graphrag.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_trails")
public class AuditTrailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;

    @Column(columnDefinition = "TEXT")
    private String details;

    private String version;
    private String agentPersona;
    private Instant timestamp;

    public AuditTrailEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getAgentPersona() { return agentPersona; }
    public void setAgentPersona(String agentPersona) { this.agentPersona = agentPersona; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
