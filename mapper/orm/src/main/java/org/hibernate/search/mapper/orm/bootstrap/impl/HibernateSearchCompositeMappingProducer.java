/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.orm.bootstrap.impl;

import java.util.Optional;

import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.search.engine.cfg.ConfigurationPropertySource;
import org.hibernate.search.mapper.orm.bootstrap.spi.HibernateSearchOrmMappingProducer;
import org.hibernate.search.mapper.orm.cfg.HibernateOrmMapperSettings;
import org.hibernate.search.mapper.orm.common.impl.HibernateOrmUtils;

public class HibernateSearchCompositeMappingProducer implements AdditionalMappingContributor {

	@Override
	public String getContributorName() {
		return "hibernate-search";
	}

	@Override
	public void contribute(AdditionalMappingContributions contributions, InFlightMetadataCollector metadata,
			ResourceStreamLocator resourceStreamLocator, MetadataBuildingContext buildingContext) {
		Optional<HibernateSearchPreIntegrationService> preIntegrationServiceOptional =
				HibernateOrmUtils.getServiceOrEmpty( buildingContext.getBootstrapContext().getServiceRegistry(),
						HibernateSearchPreIntegrationService.class );
		if ( !preIntegrationServiceOptional.isPresent() ) {
			return;
		}
		HibernateSearchPreIntegrationService preIntegrationService = preIntegrationServiceOptional.get();

		ConfigurationPropertySource propertySource = preIntegrationService.propertySource()
				.withMask( HibernateOrmMapperSettings.Radicals.COORDINATION );

		for ( HibernateSearchOrmMappingProducer mappingProducer : preIntegrationService
				.coordinationStrategyConfiguration().mappingProducers() ) {

			mappingProducer.produceMappingContributor( propertySource, buildingContext ).accept( contributions );
		}
	}
}
