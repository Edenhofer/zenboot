package org.zenboot.portal

class Host {

    String ipAddress
    String macAddress
    Date creationDate
    Date expiryDate
    String instanceId
    HostState state = HostState.UNKNOWN
    Hostname hostname
    List dnsEntries = []
    Customer owner
    Environment environment

    static hasMany = [dnsEntries:DnsEntry]

    static mapping = {
        dnsEntries cascade: 'all-delete-orphan'
        hostname cascade: 'all'
    }

    static constraints = {
        ipAddress(blank:false, length:7..15)
        macAddress(blank:false)
        hostname(nullable:false)
        instanceId(blank:false)
        state(nullable:false)
        hostname(nullable:false)
        environment(nullable:false)
    }

    def beforeInsert = { creationDate = new Date() }
    
    String toString() {
        return "${this.hostname} (${this.ipAddress})"
    }
}
