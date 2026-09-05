#include "oop/field.h"

#include <string.h>
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * CLASS: Field (oop/field.c)
 * LEVEL: L1 — File Metadata (field descriptor record)
 * ============================================================================
 * Field descriptor (one column in a Class).
 *
 * STRUCT FIELDS (Mirroring oop/field.h):
 * ----------------------------------------------------------------------------
 *   Field {
 *     char name[32]; // field name for spotlight search (VARIABLE_NAME_SIZE)
 *     uint32_t size; // byte size or classId for isStruct
 *     uint32_t offset; // unified singleton offset
 *     uint32_t stream1Offset; // hot primitive stream offset
 *     uint32_t stream2Offset; // nested struct stream offset
 *     bool isStruct; // true if field is a compound sub-struct
 *   }
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - Field_init()
 * ============================================================================
 */


// field.c — no logic yet, helpers live in class.c (size resolve, align).
// Kept as separate TU so Class and Field have 1:1 files as requested.
