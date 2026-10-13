package com.certichain.institution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "institutions")
public class InstitutionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "signatory_name", nullable = false)
    private String signatoryName = "Registrar";

    @Column(name = "signatory_title", nullable = false)
    private String signatoryTitle = "Registrar";

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSignatoryName() {
        return signatoryName == null || signatoryName.isBlank() ? "Registrar" : signatoryName;
    }

    public void setSignatoryName(String signatoryName) {
        this.signatoryName = signatoryName;
    }

    public String getSignatoryTitle() {
        return signatoryTitle == null || signatoryTitle.isBlank() ? "Registrar" : signatoryTitle;
    }

    public void setSignatoryTitle(String signatoryTitle) {
        this.signatoryTitle = signatoryTitle;
    }
}
