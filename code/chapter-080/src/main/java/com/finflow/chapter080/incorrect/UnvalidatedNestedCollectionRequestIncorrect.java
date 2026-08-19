package com.finflow.chapter080.incorrect;

import com.finflow.chapter080.domain.SplitAllocation;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UnvalidatedNestedCollectionRequestIncorrect(
    // INCORRECT: Missing @Valid annotation!
    // The items inside the list will NOT be validated.
    @NotNull
    List<SplitAllocation> splitAllocations
) {
}
