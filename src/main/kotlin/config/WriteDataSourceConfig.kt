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
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * Write store wiring (source of truth: `order_events`).
 *
 * Marked @Primary so it's the default datasource / entity-manager / transaction-manager.
 * Flyway runs `db/migration/write` before the entity manager validates the mappings.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = ["com.ikea.o360.repository.write"],
    entityManagerFactoryRef = "writeEntityManagerFactory",
    transactionManagerRef = "writeTransactionManager"
)
class WriteDataSourceConfig(
    @Value("\${write.datasource.url}") private val url: String,
    @Value("\${write.datasource.username}") private val username: String,
    @Value("\${write.datasource.password}") private val password: String
) {

    @Primary
    @Bean
    fun writeDataSource(): DataSource {
        val config = HikariConfig()
        config.jdbcUrl = url
        config.username = username
        config.password = password
        config.driverClassName = "org.postgresql.Driver"
        config.poolName = "write-pool"
        return HikariDataSource(config)
    }

    /** Create the write schema (order_events) before the EMF validates against it. */
    @Bean(initMethod = "migrate")
    fun writeFlyway(@Qualifier("writeDataSource") dataSource: DataSource): Flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/write")
            .load()

    @Primary
    @Bean
    @DependsOn("writeFlyway")
    fun writeEntityManagerFactory(
        @Qualifier("writeDataSource") dataSource: DataSource
    ): LocalContainerEntityManagerFactoryBean {
        val emf = LocalContainerEntityManagerFactoryBean()
        emf.dataSource = dataSource
        emf.setPackagesToScan("com.ikea.o360.domain.write")
        emf.persistenceUnitName = "write"
        emf.jpaVendorAdapter = HibernateJpaVendorAdapter()
        emf.setJpaPropertyMap(
            mapOf(
                "hibernate.hbm2ddl.auto" to "validate",
                "hibernate.dialect" to "org.hibernate.dialect.PostgreSQLDialect"
            )
        )
        return emf
    }

    @Primary
    @Bean
    fun writeTransactionManager(
        @Qualifier("writeEntityManagerFactory") emf: EntityManagerFactory
    ): PlatformTransactionManager = JpaTransactionManager(emf)
}

