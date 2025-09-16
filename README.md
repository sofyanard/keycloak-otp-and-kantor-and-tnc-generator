# Custom Login Handler for Keycloak

To generate OTP and populate list of offices eligible for a specific user id, accessible thru rest api. 

## How To
- build and compile
- put into deployment folder
- open "Authentication", create new
- create "rest api", add new execution.
- create "ATRBPN OTP-Kantor-TnC Login API" with "REQUIRED"
- add this in the ```standalone.xml```:
```
<subsystem xmlns="urn:jboss:domain:naming:2.0">
    <bindings>
        .....
        .....
        .....
        <simple name="java:/tncApiBaseUrl" value="{ATRBPN TnC API URL}" type="java.lang.String"/>
    </bindings>
    <remote-naming/>
</subsystem>
```