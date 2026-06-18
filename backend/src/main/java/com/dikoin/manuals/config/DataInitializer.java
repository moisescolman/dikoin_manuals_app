package com.dikoin.manuals.config;

import com.dikoin.manuals.entidades.*;
import com.dikoin.manuals.enums.*;
import com.dikoin.manuals.repositorios.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final ManualRepository manualRepository;
    private final ManualVersionRepository manualVersionRepository;
    private final TemplateRepository templateRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedRoles();
        seedUsers();
        seedProductsAndManuals();
        seedTemplate();
    }

    private void seedRoles() {
        saveRole(UserRole.ADMIN, "Administracion interna");
        saveRole(UserRole.TECNICO, "Tecnico editor de manuales");
        saveRole(UserRole.REVISOR, "Revisor tecnico");
        saveRole(UserRole.CLIENTE, "Cliente con acceso solo lectura");
    }

    private void saveRole(UserRole role, String description) {
        roleRepository.findByName(role)
                .orElseGet(() -> roleRepository.save(Role.builder().name(role).description(description).build()));
    }

    private void seedUsers() {
        saveUser("Administrador DIKOIN", "admin@dikoin.local", UserRole.ADMIN);
        saveUser("Tecnico DIKOIN", "tecnico@dikoin.local", UserRole.TECNICO);
        saveUser("Revisor DIKOIN", "revisor@dikoin.local", UserRole.REVISOR);
        saveUser("Cliente Demo", "cliente@dikoin.local", UserRole.CLIENTE);
    }

    private void saveUser(String name, String email, UserRole roleName) {
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        Role role = roleRepository.findByName(roleName).orElseThrow();
        appUserRepository.save(AppUser.builder()
                .fullName(name)
                .email(email)
                .passwordHash(passwordEncoder.encode("1234"))
                .role(role)
                .active(true)
                .build());
    }

    private void seedProductsAndManuals() {
        Product flb = productRepository.findByCodeIgnoreCase("FLB10.1")
                .orElseGet(() -> productRepository.save(Product.builder()
                        .code("FLB10.1")
                        .name("Equipo FLB10.1")
                        .family("FLB")
                        .category("Practicas")
                        .description("Producto demo para manual de practicas.")
                        .active(true)
                        .build()));

        Product hy = productRepository.findByCodeIgnoreCase("HY100")
                .orElseGet(() -> productRepository.save(Product.builder()
                        .code("HY100")
                        .name("Equipo HY100")
                        .family("HY")
                        .category("Hidraulica")
                        .description("Producto demo para manual de practicas de hidraulica.")
                        .active(true)
                        .build()));

        createManualIfMissing("DMP-FLB10.1-2601", "Manual de practicas FLB10.1", flb, true);
        createManualIfMissing("DMP-HY100-2501", "Manual de practicas HY100", hy, false);
    }

    private void createManualIfMissing(String code, String title, Product product, boolean published) {
        if (manualRepository.existsByCodeIgnoreCase(code)) {
            return;
        }
        Manual manual = manualRepository.save(Manual.builder()
                .code(code)
                .title(title)
                .category("Practicas")
                .product(product)
                .build());

        ManualVersion version = ManualVersion.builder()
                .manual(manual)
                .versionNumber(published ? "1.0" : "0.3")
                .status(published ? ManualStatus.PUBLISHED : ManualStatus.DRAFT)
                .active(true)
                .esReady(true)
                .enReady(false)
                .changeNotes("Datos demo iniciales")
                .build();

        addSection(version, 1, "1", "Introduccion", "Este manual describe el uso del equipo y las practicas asociadas al producto.");
        addSection(version, 2, "2", "Descripcion del equipo", "El equipo esta compuesto por modulos, sensores, conexiones y elementos de control.");
        addTableSection(version, 3);
        addSection(version, 4, "3", "Procedimiento de practica", "Comprobar las conexiones antes de iniciar la practica. Registrar los datos obtenidos durante el funcionamiento.");
        addFormulaSection(version, 5);
        manualVersionRepository.save(version);
    }

    private void addSection(ManualVersion version, int sort, String number, String title, String text) {
        ManualSection section = ManualSection.builder()
                .manualVersion(version)
                .sortOrder(sort)
                .sectionNumber(number)
                .titleEs(title)
                .titleEn(null)
                .completionStatus("READY")
                .build();
        section.getBlocks().add(ManualBlock.builder()
                .section(section)
                .sortOrder(1)
                .blockType(BlockType.PARAGRAPH)
                .languageCode(LanguageCode.ES)
                .contentJson("{\"type\":\"paragraph\",\"text\":\"" + text + "\"}")
                .build());
        version.getSections().add(section);
    }

    private void addTableSection(ManualVersion version, int sort) {
        ManualSection section = ManualSection.builder()
                .manualVersion(version)
                .sortOrder(sort)
                .sectionNumber("2.1")
                .titleEs("Componentes principales")
                .completionStatus("READY")
                .build();
        section.getBlocks().add(ManualBlock.builder()
                .section(section)
                .sortOrder(1)
                .blockType(BlockType.TABLE)
                .languageCode(LanguageCode.ES)
                .contentJson("{\"type\":\"table\",\"columns\":[\"N\",\"Componente\",\"Descripcion\",\"Cantidad\"],\"rows\":[[\"1\",\"Modulo principal\",\"Estructura base del equipo\",\"1\"],[\"2\",\"Sensor\",\"Elemento de medicion\",\"2\"]]}")
                .build());
        version.getSections().add(section);
    }

    private void addFormulaSection(ManualVersion version, int sort) {
        ManualSection section = ManualSection.builder()
                .manualVersion(version)
                .sortOrder(sort)
                .sectionNumber("3.1")
                .titleEs("Estudio del funcionamiento")
                .completionStatus("READY")
                .build();
        section.getBlocks().add(ManualBlock.builder()
                .section(section)
                .sortOrder(1)
                .blockType(BlockType.FORMULA)
                .languageCode(LanguageCode.ES)
                .contentJson("{\"type\":\"formula\",\"latex\":\"P_{hidraulica}=\\\\rho\\\\cdot g\\\\cdot H\\\\cdot Q\"}")
                .build());
        version.getSections().add(section);
    }

    private void seedTemplate() {
        if (templateRepository.findByActiveTrue().isPresent()) {
            return;
        }
        templateRepository.save(Template.builder()
                .name("Plantilla corporativa DIKOIN")
                .companyName("DIKOIN")
                .contactEmail("info@dikoin.com")
                .contactPhone("+34 000 000 000")
                .website("https://www.dikoin.com")
                .logoPath("assets/logos/logo-placeholder.png")
                .headerConfigJson("{\"showLogo\":true,\"showManualCode\":true}")
                .footerConfigJson("{\"showContact\":true,\"showPageNumber\":true}")
                .active(true)
                .build());
    }
}
