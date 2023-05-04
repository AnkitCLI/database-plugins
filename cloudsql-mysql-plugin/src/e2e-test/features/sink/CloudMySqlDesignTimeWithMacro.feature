Feature: CloudMySql sink- Verify CloudMySql sink plugin design time macro scenarios

  Scenario: To verify CloudMySql sink plugin validation with macro enabled fields for connection section
    Given Open Datafusion Project to configure pipeline
    When Expand Plugin group in the LHS plugins list: "Sink"
    When Select plugin: "CloudSQL MySQL" from the plugins list as: "Sink"
    Then Navigate to the properties page of plugin: "CloudSQL MySQL"
    Then Click on the Macro button of Property: "jdbcPluginName" and set the value to: "cloudsql-mysql"
    Then Enter input plugin property: "connectionName" with value: "cdf-athena:us-central1:sql-automation-test-instance"
    Then Select radio button plugin property: "instanceType" with value: "public"
    Then Click on the Macro button of Property: "user" and set the value to: "username"
    Then Click on the Macro button of Property: "password" and set the value to: "password"
    Then Click on the Macro button of Property: "connectionArguments" and set the value to: "connectionArgumentsList"
    Then Enter input plugin property: "referenceName" with value: "sourceRef"
    Then Replace input plugin property: "database" with value: "TestDatabase"
    Then Click on the Validate button
#    Then Validate "CloudSQL MySQL" plugin properties
    Then Close the Plugin Properties page

  Scenario: To verify cloudsql sink plugin validation with macro enabled fields for basic section
    Given Open Datafusion Project to configure pipeline
    When Expand Plugin group in the LHS plugins list: "Sink"
    When Select plugin: "CloudSQL MySQL" from the plugins list as: "Sink"
    Then Navigate to the properties page of plugin: "CloudSQL MySQL"
    Then Select dropdown plugin property: "select-jdbcPluginName" with option value: "cloudsql-mysql"
    Then Enter input plugin property: "connectionName" with value: "cdf-athena:us-central1:sql-automation-test-instance"
    Then Select radio button plugin property: "instanceType" with value: "public"
    Then Replace input plugin property: "user" with value: "username" for Credentials and Authorization related fields
    Then Replace input plugin property: "password" with value: "password" for Credentials and Authorization related fields
    Then Enter input plugin property: "referenceName" with value: "sourceRef"
    Then Replace input plugin property: "database" with value: "TestDatabase"
    Then Click on the Macro button of Property: "tableName" and set the value to: "mytable"
    Then Click on the Validate button
#    Then Validate "CloudSQL MySQL" plugin properties
    Then Close the Plugin Properties page