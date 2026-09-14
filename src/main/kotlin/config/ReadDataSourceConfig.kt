package com.ikea.o360.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * Read store wiring (projection target: `orders`, `order_items`).
 *
 * Flyway runs `db/migration/read` before the entity manager validates the mappings.
 * The projection and query services target `readTransactionManager` explicitly.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = ["com.ikea.o360.repository.read"],
    entityManagerFactoryRef = "readEntityManagerFactory",
    transactionManagerRef = "readTransactionManager"
)
class ReadDataSourceConfig(
    @Value("\${read.datasource.url}") private val url: String,
    @Value("\${read.datasource.username}") private val username: String,
    @Value("\${read.datasource.password}") private val password: String
) {

    @Bean
    fun readDataSource(): DataSource {
        val config = HikariConfig()
        config.jdbcUrl = url
        config.username = username
        config.password = password
        config.driverClassName = "org.postgresql.Driver"
        config.poolName = "read-pool"
        return HikariDataSource(config)
    }

    /** Create the read schema (orders / order_items) before the EMF validates against it. */
    @Bean(initMethod = "migrate")
    fun readFlyway(@Qualifier("readDataSource") dataSource: DataSource): Flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/read")
            .load()

    @Bean
    @DependsOn("readFlyway")
    fun readEntityManagerFactory(
        @Qualifier("readDataSource") dataSource: DataSource
    ): LocalContainerEntityManagerFactoryBean {
        val emf = LocalContainerEntityManagerFactoryBean()
        emf.dataSource = dataSource
        emf.setPackagesToScan("com.ikea.o360.domain.read")
        emf.persistenceUnitName = "read"
        emf.jpaVendorAdapter = HibernateJpaVendorAdapter()
        emf.setJpaPropertyMap(
            mapOf(
                "hibernate.hbm2ddl.auto" to "validate",
                "hibernate.dialect" to "org.hibernate.dialect.PostgreSQLDialect"
            )
        )
        return emf
    }

    @Bean
    fun readTransactionManager(
        @Qualifier("readEntityManagerFactory") emf: EntityManagerFactory
    ): PlatformTransactionManager = JpaTransactionManager(emf)
}

