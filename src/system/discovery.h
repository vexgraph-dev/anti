#ifndef SYSTEM_DISCOVERY_H
#define SYSTEM_DISCOVERY_H

#include <stdbool.h>

// system/discovery.h — native hardware, display, and GPU probe (Legacy: system/SystemDiscovery.java)

void SystemDiscovery_bootstrap(void);
void SystemDiscovery_refresh(void);
bool SystemDiscovery_isBootstrapped(void);

#endif
