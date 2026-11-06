package kr.sedanji.chef;

import jakarta.persistence.*;

@Entity
@Table(name = "chefs")
public class ChefEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Version private long version;

    protected ChefEntity() {}
    public ChefEntity(String name) { this.name = name; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public void correctName(String value) {
        if (value == null || value.isBlank() || value.length() > 100) throw new IllegalArgumentException("셰프 이름을 확인하세요.");
        name = value.strip();
    }
}
