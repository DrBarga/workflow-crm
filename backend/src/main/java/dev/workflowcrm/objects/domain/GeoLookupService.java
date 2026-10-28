package dev.workflowcrm.objects.domain;

import dev.workflowcrm.integrations.api.*;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.users.api.RegionBoundaryQuery;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GeoLookupService {
    private final ObjectCardAccess access;
    private final ForwardGeocoder forward;
    private final ReverseGeocoder reverse;
    private final RegionBoundaryQuery regions;
    private final UkraineBoundary ukraine;

    GeoLookupService(
            ObjectCardAccess access,
            ForwardGeocoder forward,
            ReverseGeocoder reverse,
            RegionBoundaryQuery regions,
            UkraineBoundary ukraine) {
        this.access = access;
        this.forward = forward;
        this.reverse = reverse;
        this.regions = regions;
        this.ukraine = ukraine;
    }

    public List<ForwardGeocoder.Location> search(UUID actor, String query, int limit) {
        access.require(actor);
        if (limit < 1 || limit > 6) throw new UnprocessableInputException("limit", "Limit must be between 1 and 6");
        String q = query == null ? "" : query.strip();
        if (q.length() < 3 || q.length() > 200 || q.codePoints().anyMatch(Character::isISOControl))
            throw new UnprocessableInputException("q", "Search must be 3 to 200 characters without control characters");
        return forward.search(q).stream().limit(limit).toList();
    }

    public record Address(
            String address,
            UUID regionId,
            boolean requiresRegionSelection,
            BigDecimal latitude,
            BigDecimal longitude) {}

    public Address reverse(UUID actor, BigDecimal longitude, BigDecimal latitude) {
        var user = access.require(actor);
        EntranceCoordinates coordinates;
        try {
            coordinates = new EntranceCoordinates(longitude, latitude);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new UnprocessableInputException(
                    "coordinates", "Provide valid WGS84 coordinates with at most seven decimal places");
        }
        if (!ukraine.contains(coordinates))
            throw new UnprocessableInputException("coordinates", "Point must be inside Ukraine");
        var address = reverse.resolve(longitude, latitude);
        var region = regions.locate(longitude, latitude)
                .filter(id -> !Set.of("MANAGER", "REGIONAL_MANAGER").contains(user.role()) || user.belongsTo(id))
                .orElse(null);
        return new Address(address.address(), region, region == null, latitude, longitude);
    }
}
