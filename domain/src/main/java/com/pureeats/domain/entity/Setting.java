package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Setting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Backtick-quoted: "key" is a reserved word in MySQL/MariaDB and breaks unquoted DDL/DML. */
    @Column(name = "`key`", nullable = false)
    private String key;

    /** Backtick-quoted like "key" above: "value" is also a reserved word (at least under H2's MySQL test mode) and breaks unquoted DDL/DML. */
    @Lob
    @Column(name = "`value`")
    private String value;
}
