package com.foodlog.service;

import com.foodlog.config.AppProperties;
import com.foodlog.repository.FoodItemRepository;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Seeds the bundled sample foods into an empty database, then loads the catalog into memory. */
@Component
public class CatalogInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogInitializer.class);

    private final AppProperties props;
    private final FoodItemRepository repository;
    private final FoodImportService importService;
    private final FoodCatalog catalog;

    public CatalogInitializer(AppProperties props, FoodItemRepository repository,
                              FoodImportService importService, FoodCatalog catalog) {
        this.props = props;
        this.repository = repository;
        this.importService = importService;
        this.catalog = catalog;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (props.seedSampleData() && repository.count() == 0) {
            try (InputStream in = new ClassPathResource("data/foods_sample.csv").getInputStream()) {
                int n = importService.importCsv(in);
                log.info("Seeded {} sample foods (approximate values, not the official Swiss database)", n);
            }
        }
        catalog.reload();
        log.info("Food catalog loaded: {} foods", catalog.all().size());
    }
}
