package com.possaas.service;

import com.possaas.config.TenantContext;
import com.possaas.domain.Branch;
import com.possaas.domain.BranchInventory;
import com.possaas.domain.InventoryMovement;
import com.possaas.domain.MovementType;
import com.possaas.domain.PosTerminal;
import com.possaas.domain.Product;
import com.possaas.domain.Tenant;
import com.possaas.dto.onboarding.OnboardingDtos;
import com.possaas.repository.BranchInventoryRepository;
import com.possaas.repository.BranchRepository;
import com.possaas.repository.InventoryMovementRepository;
import com.possaas.repository.PosTerminalRepository;
import com.possaas.repository.ProductRepository;
import com.possaas.repository.TenantRepository;
import com.possaas.util.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final Set<String> TAX_REGIMES = Set.of("RESPONSABLE_IVA", "NO_RESPONSABLE_IVA", "REGIMEN_SIMPLE");

    private final TenantRepository tenantRepository;
    private final PosTerminalRepository terminalRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final BranchInventoryRepository branchInventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final ElectronicBillingProvider billingProvider;

    @Transactional(readOnly = true)
    public OnboardingDtos.OnboardingStatusResponse status() {
        UUID tenantId = TenantContext.require();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        PosTerminal terminal = resolveMainTerminal(tenantId);
        return toStatus(tenant, terminal);
    }

    @Transactional
    public OnboardingDtos.OnboardingStatusResponse saveFiscalStep(OnboardingDtos.FiscalStepRequest req) {
        UUID tenantId = TenantContext.require();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        String regime = req.taxRegime().trim().toUpperCase(Locale.ROOT);
        if (!TAX_REGIMES.contains(regime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Regimen tributario invalido");
        }
        tenant.setTaxRegime(regime);
        tenant.setFiscalResponsibilities(req.fiscalResponsibilities().trim());
        tenant.setCity(blankToNull(req.city()));
        tenant.setAddress(blankToNull(req.address()));
        tenant.setPhone(blankToNull(req.phone()));
        tenant.setReceiptFooterMessage(blankToNull(req.receiptFooterMessage()) == null ? "Gracias por su compra" : req.receiptFooterMessage().trim());
        if (Boolean.FALSE.equals(tenant.getOnboardingCompleted()) && tenant.getOnboardingStep() != null && tenant.getOnboardingStep() < 2) {
            tenant.setOnboardingStep(2);
        } else if (Boolean.FALSE.equals(tenant.getOnboardingCompleted()) && tenant.getOnboardingStep() == null) {
            tenant.setOnboardingStep(2);
        }
        tenantRepository.save(tenant);
        return toStatus(tenant, resolveMainTerminal(tenantId));
    }

    @Transactional
    public OnboardingDtos.DianStepResponse saveDianStep(OnboardingDtos.DianStepRequest req) {
        UUID tenantId = TenantContext.require();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        PosTerminal terminal = resolveMainTerminal(tenantId);
        if (terminal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro la caja principal");
        }
        if (req.rangeFrom() == null || req.rangeTo() == null || req.rangeFrom() < 1 || req.rangeTo() < req.rangeFrom()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rango DIAN invalido");
        }
        if (req.dianValidFrom() != null && req.dianValidTo() != null && req.dianValidTo().isBefore(req.dianValidFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La vigencia DIAN es invalida");
        }

        boolean doVerify = req.verify() == null || Boolean.TRUE.equals(req.verify());
        ElectronicBillingProvider.DianVerificationResult verification = doVerify
                ? billingProvider.verifyConfiguration(new ElectronicBillingProvider.DianVerificationRequest(
                        tenantId,
                        req.dianPrefix().trim().toUpperCase(Locale.ROOT),
                        req.rangeFrom(),
                        req.rangeTo(),
                        blankToNull(req.dianResolutionNumber()),
                        req.dianResolutionDate(),
                        req.dianValidFrom(),
                        req.dianValidTo(),
                        blankToNull(req.dianTechnicalKey())))
                : new ElectronicBillingProvider.DianVerificationResult(true, "SKIPPED", "Verificacion omitida por el usuario");

        if (!verification.ok()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verificacion DIAN fallida: " + verification.message());
        }

        terminal.setDianPrefix(req.dianPrefix().trim().toUpperCase(Locale.ROOT));
        terminal.setRangeFrom(req.rangeFrom());
        terminal.setRangeTo(req.rangeTo());
        terminal.setDianResolutionNumber(blankToNull(req.dianResolutionNumber()));
        terminal.setDianResolutionDate(req.dianResolutionDate());
        terminal.setDianValidFrom(req.dianValidFrom());
        terminal.setDianValidTo(req.dianValidTo());
        terminal.setDianTechnicalKey(blankToNull(req.dianTechnicalKey()));
        terminal.setUpdatedAt(Instant.now());
        terminalRepository.save(terminal);

        if (Boolean.FALSE.equals(tenant.getOnboardingCompleted()) && (tenant.getOnboardingStep() == null || tenant.getOnboardingStep() < 3)) {
            tenant.setOnboardingStep(3);
            tenantRepository.save(tenant);
        }
        return new OnboardingDtos.DianStepResponse(verification.ok(), verification.mode(), verification.message(), toStatus(tenant, terminal));
    }

    @Transactional
    public OnboardingDtos.SeedCatalogResponse seedCatalog(OnboardingDtos.SeedCatalogRequest req) {
        UUID tenantId = TenantContext.require();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        String action = req == null || req.action() == null ? "SKIP" : req.action().trim().toUpperCase(Locale.ROOT);
        int created = 0;
        String message;
        if ("SEED".equals(action)) {
            String niche = req.niche() == null ? "MINIMERCADO" : req.niche().trim().toUpperCase(Locale.ROOT);
            created = seedByNiche(tenantId, niche);
            message = created > 0 ? "Catalogo de prueba cargado (" + created + " productos)." : "El catalogo ya tiene productos, no se sembraron duplicados.";
        } else if ("SKIP".equals(action)) {
            message = "Catalogo inicial omitido. Puedes cargar tu Excel desde Cargue Masivo.";
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Accion de catalogo invalida");
        }

        tenant.setOnboardingStep(4);
        tenant.setOnboardingCompleted(true);
        tenantRepository.save(tenant);
        return new OnboardingDtos.SeedCatalogResponse(action, created, message, toStatus(tenant, resolveMainTerminal(tenantId)));
    }

    private int seedByNiche(UUID tenantId, String niche) {
        List<SeedProduct> seeds = switch (niche) {
            case "FERRETERIA" -> ferreteria();
            case "DROGUERIA" -> drogueria();
            case "MISCELANEA", "MISCELAENA" -> miscelanea();
            default -> minimercado();
        };

        Branch main = branchRepository.findByTenantIdAndMainTrue(tenantId)
                .orElseGet(() -> branchRepository.findByTenantIdOrderByMainDescNameAsc(tenantId).stream().findFirst().orElse(null));
        if (main == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No existe sede principal para sembrar inventario");
        }

        Instant now = Instant.now();
        int created = 0;
        for (SeedProduct s : seeds) {
            if (productRepository.findByTenantIdAndSku(tenantId, s.sku).isPresent()) {
                continue;
            }
            Product p = new Product();
            p.setId(UuidV7.generate());
            p.setTenantId(tenantId);
            p.setSku(s.sku);
            p.setBarcode(s.barcode);
            p.setName(s.name);
            p.setSalePrice(s.price);
            p.setTaxType(s.taxRate.compareTo(BigDecimal.ZERO) > 0 ? "IVA" : "EXENTO");
            p.setTaxRate(s.taxRate);
            p.setActive(true);
            p.setUpdatedAt(now);
            productRepository.save(p);

            BranchInventory inv = new BranchInventory();
            inv.setId(UuidV7.generate());
            inv.setTenantId(tenantId);
            inv.setBranchId(main.getId());
            inv.setProductId(p.getId());
            inv.setCurrentStock(s.stock);
            inv.setMinStock(s.minStock);
            inv.setUpdatedAt(now);
            branchInventoryRepository.save(inv);

            if (s.stock.compareTo(BigDecimal.ZERO) > 0) {
                InventoryMovement mv = new InventoryMovement();
                mv.setId(UuidV7.generate());
                mv.setTenantId(tenantId);
                mv.setBranchId(main.getId());
                mv.setProductId(p.getId());
                mv.setMovementType(MovementType.INITIAL_BALANCE);
                mv.setQuantity(s.stock);
                mv.setPreviousStock(BigDecimal.ZERO);
                mv.setNewStock(s.stock);
                mv.setNotes("Carga inicial catalogo plantilla (" + niche + ")");
                mv.setCreatedAt(now);
                movementRepository.save(mv);
            }
            created++;
        }
        return created;
    }

    private record SeedProduct(String sku, String barcode, String name, BigDecimal price, BigDecimal taxRate, BigDecimal stock, BigDecimal minStock) {}

    private List<SeedProduct> minimercado() {
        List<SeedProduct> l = new ArrayList<>();
        l.add(new SeedProduct("MIN-001", "7700001000011", "Arroz 500g", bd("2800"), bd("0.0000"), bd("30"), bd("10")));
        l.add(new SeedProduct("MIN-002", "7700001000028", "Aceite 1L", bd("12500"), bd("0.1900"), bd("20"), bd("5")));
        l.add(new SeedProduct("MIN-003", "7700001000035", "Azucar 1kg", bd("5200"), bd("0.0500"), bd("25"), bd("8")));
        l.add(new SeedProduct("MIN-004", "7700001000042", "Sal 500g", bd("1800"), bd("0.0000"), bd("40"), bd("10")));
        l.add(new SeedProduct("MIN-005", "7700001000059", "Leche entera 1L", bd("4800"), bd("0.0000"), bd("24"), bd("12")));
        l.add(new SeedProduct("MIN-006", "7700001000066", "Huevos x12", bd("9800"), bd("0.0000"), bd("18"), bd("6")));
        l.add(new SeedProduct("MIN-007", "7700001000073", "Pan tajado", bd("6500"), bd("0.0000"), bd("15"), bd("5")));
        l.add(new SeedProduct("MIN-008", "7700001000080", "Cafe 250g", bd("14500"), bd("0.0500"), bd("20"), bd("6")));
        l.add(new SeedProduct("MIN-009", "7700001000097", "Gaseosa 1.5L", bd("5200"), bd("0.1900"), bd("30"), bd("10")));
        l.add(new SeedProduct("MIN-010", "7700001000103", "Agua 600ml", bd("2200"), bd("0.1900"), bd("40"), bd("12")));
        l.add(new SeedProduct("MIN-011", "7700001000110", "Galletas surtidas", bd("3900"), bd("0.1900"), bd("25"), bd("8")));
        l.add(new SeedProduct("MIN-012", "7700001000127", "Atun lata", bd("7200"), bd("0.1900"), bd("20"), bd("6")));
        l.add(new SeedProduct("MIN-013", "7700001000134", "Pasta espagueti", bd("3600"), bd("0.0500"), bd("30"), bd("10")));
        l.add(new SeedProduct("MIN-014", "7700001000141", "Jabon barra", bd("4200"), bd("0.1900"), bd("20"), bd("6")));
        l.add(new SeedProduct("MIN-015", "7700001000158", "Papel higienico x4", bd("8900"), bd("0.1900"), bd("18"), bd("6")));
        return l;
    }

    private List<SeedProduct> ferreteria() {
        List<SeedProduct> l = new ArrayList<>();
        l.add(new SeedProduct("FER-001", "7700002000018", "Martillo carpintero", bd("32000"), bd("0.1900"), bd("10"), bd("3")));
        l.add(new SeedProduct("FER-002", "7700002000025", "Destornillador plano", bd("8500"), bd("0.1900"), bd("20"), bd("5")));
        l.add(new SeedProduct("FER-003", "7700002000032", "Destornillador estrella", bd("8500"), bd("0.1900"), bd("20"), bd("5")));
        l.add(new SeedProduct("FER-004", "7700002000049", "Cinta teflon", bd("3500"), bd("0.1900"), bd("30"), bd("10")));
        l.add(new SeedProduct("FER-005", "7700002000056", "Tornillos mixtos x50", bd("12000"), bd("0.1900"), bd("25"), bd("8")));
        l.add(new SeedProduct("FER-006", "7700002000063", "Clavos 2 pulgadas x100", bd("9000"), bd("0.1900"), bd("25"), bd("8")));
        l.add(new SeedProduct("FER-007", "7700002000070", "Llave ajustable 10\"", bd("28000"), bd("0.1900"), bd("8"), bd("2")));
        l.add(new SeedProduct("FER-008", "7700002000087", "Flexometro 5m", bd("18000"), bd("0.1900"), bd("12"), bd("4")));
        l.add(new SeedProduct("FER-009", "7700002000094", "Silicona transparente", bd("14500"), bd("0.1900"), bd("15"), bd("5")));
        l.add(new SeedProduct("FER-010", "7700002000100", "Brocha 2\"", bd("6500"), bd("0.1900"), bd("20"), bd("6")));
        l.add(new SeedProduct("FER-011", "7700002000117", "Lija grano medio", bd("2500"), bd("0.1900"), bd("40"), bd("10")));
        l.add(new SeedProduct("FER-012", "7700002000124", "Interruptor sencillo", bd("7800"), bd("0.1900"), bd("20"), bd("6")));
        l.add(new SeedProduct("FER-013", "7700002000131", "Bombillo LED 9W", bd("9500"), bd("0.1900"), bd("25"), bd("8")));
        l.add(new SeedProduct("FER-014", "7700002000148", "Guantes de trabajo", bd("12000"), bd("0.1900"), bd("15"), bd("5")));
        l.add(new SeedProduct("FER-015", "7700002000155", "Candado mediano", bd("22000"), bd("0.1900"), bd("10"), bd("3")));
        return l;
    }

    private List<SeedProduct> drogueria() {
        List<SeedProduct> l = new ArrayList<>();
        l.add(new SeedProduct("DRO-001", "7700003000015", "Acetaminofen 500mg x10", bd("4500"), bd("0.0000"), bd("30"), bd("10")));
        l.add(new SeedProduct("DRO-002", "7700003000022", "Ibuprofeno 400mg x10", bd("5200"), bd("0.0000"), bd("30"), bd("10")));
        l.add(new SeedProduct("DRO-003", "7700003000039", "Alcohol antiseptico 250ml", bd("6800"), bd("0.1900"), bd("20"), bd("6")));
        l.add(new SeedProduct("DRO-004", "7700003000046", "Gasas esteriles x10", bd("7500"), bd("0.0000"), bd("15"), bd("5")));
        l.add(new SeedProduct("DRO-005", "7700003000053", "Curitas x20", bd("5900"), bd("0.1900"), bd("20"), bd("6")));
        l.add(new SeedProduct("DRO-006", "7700003000060", "Suero oral", bd("4200"), bd("0.0000"), bd("25"), bd("8")));
        l.add(new SeedProduct("DRO-007", "7700003000077", "Vitamina C x30", bd("18500"), bd("0.0000"), bd("15"), bd("5")));
        l.add(new SeedProduct("DRO-008", "7700003000084", "Gel antibacterial 120ml", bd("6500"), bd("0.1900"), bd("20"), bd("6")));
        l.add(new SeedProduct("DRO-009", "7700003000091", "Algodon 50g", bd("3800"), bd("0.1900"), bd("20"), bd("6")));
        l.add(new SeedProduct("DRO-010", "7700003000107", "Tapabocas x10", bd("9000"), bd("0.0000"), bd("25"), bd("8")));
        l.add(new SeedProduct("DRO-011", "7700003000114", "Jarabe para la tos", bd("16500"), bd("0.0000"), bd("12"), bd("4")));
        l.add(new SeedProduct("DRO-012", "7700003000121", "Termometro digital", bd("24000"), bd("0.1900"), bd("8"), bd("2")));
        l.add(new SeedProduct("DRO-013", "7700003000138", "Protector solar 50ml", bd("28500"), bd("0.1900"), bd("10"), bd("3")));
        l.add(new SeedProduct("DRO-014", "7700003000145", "Suplemento zinc x30", bd("21000"), bd("0.0000"), bd("10"), bd("3")));
        l.add(new SeedProduct("DRO-015", "7700003000152", "Agua oxigenada 120ml", bd("3500"), bd("0.1900"), bd("20"), bd("6")));
        return l;
    }

    private List<SeedProduct> miscelanea() {
        List<SeedProduct> l = new ArrayList<>();
        l.add(new SeedProduct("MIS-001", "7700004000012", "Cuaderno rayado", bd("6500"), bd("0.1900"), bd("30"), bd("10")));
        l.add(new SeedProduct("MIS-002", "7700004000029", "Esfero negro", bd("1800"), bd("0.1900"), bd("50"), bd("15")));
        l.add(new SeedProduct("MIS-003", "7700004000036", "Lapiz HB", bd("1200"), bd("0.1900"), bd("50"), bd("15")));
        l.add(new SeedProduct("MIS-004", "7700004000043", "Borrador", bd("900"), bd("0.1900"), bd("40"), bd("10")));
        l.add(new SeedProduct("MIS-005", "7700004000050", "Tijeras escolares", bd("5200"), bd("0.1900"), bd("20"), bd("6")));
        l.add(new SeedProduct("MIS-006", "7700004000067", "Pegante en barra", bd("3500"), bd("0.1900"), bd("25"), bd("8")));
        l.add(new SeedProduct("MIS-007", "7700004000074", "Resma papel carta", bd("22000"), bd("0.1900"), bd("10"), bd("3")));
        l.add(new SeedProduct("MIS-008", "7700004000081", "Carpeta plastica", bd("4500"), bd("0.1900"), bd("20"), bd("6")));
        l.add(new SeedProduct("MIS-009", "7700004000098", "Marcador permanente", bd("4200"), bd("0.1900"), bd("20"), bd("6")));
        l.add(new SeedProduct("MIS-010", "7700004000104", "Regla 30cm", bd("2500"), bd("0.1900"), bd("25"), bd("8")));
        l.add(new SeedProduct("MIS-011", "7700004000111", "Cinta adhesiva", bd("3000"), bd("0.1900"), bd("25"), bd("8")));
        l.add(new SeedProduct("MIS-012", "7700004000128", "Block notas", bd("4800"), bd("0.1900"), bd("20"), bd("6")));
        l.add(new SeedProduct("MIS-013", "7700004000135", "Resaltador", bd("3200"), bd("0.1900"), bd("25"), bd("8")));
        l.add(new SeedProduct("MIS-014", "7700004000142", "Sobres manila x10", bd("7500"), bd("0.1900"), bd("15"), bd("5")));
        l.add(new SeedProduct("MIS-015", "7700004000159", "Grapadora pequena", bd("14500"), bd("0.1900"), bd("10"), bd("3")));
        return l;
    }

    private BigDecimal bd(String v) { return new BigDecimal(v); }

    private String blankToNull(String v) { return (v == null || v.trim().isEmpty()) ? null : v.trim(); }

    private PosTerminal resolveMainTerminal(UUID tenantId) {
        return terminalRepository.findFirstByTenantIdAndName(tenantId, "Caja Principal")
                .or(() -> terminalRepository.findFirstByTenantIdAndActiveTrueOrderByDianPrefixAsc(tenantId))
                .orElse(null);
    }

    private OnboardingDtos.OnboardingStatusResponse toStatus(Tenant tenant, PosTerminal terminal) {
        OnboardingDtos.TerminalInfo t = terminal == null ? null : new OnboardingDtos.TerminalInfo(
                terminal.getId(), terminal.getName(), terminal.getDianPrefix(),
                terminal.getRangeFrom(), terminal.getRangeTo(), terminal.getCreditNotePrefix(),
                terminal.getDianResolutionNumber(), terminal.getDianResolutionDate(),
                terminal.getDianValidFrom(), terminal.getDianValidTo(), terminal.getDianTechnicalKey());
        OnboardingDtos.TenantFiscalInfo tf = new OnboardingDtos.TenantFiscalInfo(
                tenant.getId(), tenant.getNit(), tenant.getBusinessName(), tenant.getTradeName(),
                tenant.getTaxRegime(), tenant.getFiscalResponsibilities(),
                tenant.getCity(), tenant.getAddress(), tenant.getPhone(), tenant.getEmail(),
                tenant.getReceiptFooterMessage(), tenant.getPlanCode(), tenant.getSubscriptionStatus(),
                tenant.getTrialEndsAt());
        int step = tenant.getOnboardingStep() == null ? 1 : tenant.getOnboardingStep();
        return new OnboardingDtos.OnboardingStatusResponse(Boolean.TRUE.equals(tenant.getOnboardingCompleted()), step, tf, t);
    }
}
