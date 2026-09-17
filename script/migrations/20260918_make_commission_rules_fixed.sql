-- Commission rates are fixed business rules and must not be overridden by
-- values left behind by earlier versions of the revenue settings page.
DELETE FROM crawler_runtime_config
WHERE config_group='revenue'
  AND config_key IN ('leaderCommissionRate','commissionTiers','batchSiteCommissionRate');
