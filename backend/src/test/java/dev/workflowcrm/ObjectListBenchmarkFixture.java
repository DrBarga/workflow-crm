package dev.workflowcrm;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class ObjectListBenchmarkFixture {
    private ObjectListBenchmarkFixture() {}

    static void seed(JdbcTemplate jdbc, UUID manager, UUID colleague, UUID outsider, UUID region, UUID otherRegion) {
        new NamedParameterJdbcTemplate(jdbc)
                .update(
                        """
                insert into objects(id,name,address,manager_id,region_id,author_id,status_id,object_type_id,
                    entrance_longitude,entrance_latitude,object_area,created_at,building_area,connected_power,
                    contact_name,contact_role,contact_phone,investment_committee_date,investment_committee_decision,
                    contract_signed_date,preliminary_contract_signed_date,short_comment)
                select md5(n::text)::uuid,'Магазин '||lpad(n::text,6,'0'),'Вулиця Київська '||n,
                    case when n%3=0 then cast(:outsider as uuid) when n%2=0 then cast(:colleague as uuid) else cast(:manager as uuid) end,
                    case when n%3=0 then cast(:other as uuid) else cast(:region as uuid) end,cast(:manager as uuid),s.id,t.id,
                    30.5,50.4,120,'2025-01-01'::timestamptz+n*interval '10 minutes',100,25,'Власник','Контакт','+380991234567',
                    case when n%4=0 then null else date '2025-01-01'+n%300 end,
                    case when n%4=0 then null else 'APPROVED' end,
                    case when n%5=0 then null else date '2025-01-01'+n%300 end,
                    date '2027-01-01'-n%300,
                    case when n%7=0 then null else repeat('К',200) end
                from generate_series(1,50000) n
                join object_statuses s on s.code=case when n%3=0 then 'IN_PROGRESS' else 'FOUND' end
                join object_types t on t.code=case when n%2=0 then 'HANGAR' else 'BUILT_IN_PREMISES' end
                """,
                        Map.of(
                                "outsider",
                                outsider,
                                "colleague",
                                colleague,
                                "manager",
                                manager,
                                "other",
                                otherRegion,
                                "region",
                                region));
        jdbc.execute("analyze objects");
    }

    static Map<String, String> combined(UUID manager, UUID region) {
        return Map.of(
                "limit",
                "50",
                "status",
                "FOUND",
                "managerId",
                manager.toString(),
                "regionId",
                region.toString(),
                "objectType",
                "BUILT_IN_PREMISES",
                "q",
                "Київська",
                "createdFrom",
                "2025-04-01",
                "createdTo",
                "2025-09-01");
    }
}
