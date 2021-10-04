### Schema API

__GET/schemas__

__Returns:__

List containing names of available schemas.

__Response body example:__
```json
[
    "exception-test",
    "poc",
    "secret-configs"
]
```
##
__GET/schema/{schemaName}__

__Path variable:__

*schemaName* - Name of the schema, same as the branch name.

Throws exception if requested schema doesn't exists, or is the same as master.

__Returns:__

`SchemaControllerResponse` object. 

__Response body example:__

```json
{
    "commitRef": "5d2e48fecf34dcac3a6a03006fd8f2013f1a2abf",
    "resources": [
          {
            "kind": "Th2Box",
            "name": "codec8",
            "spec": {
                "image-name": "ghcr.io/th2-net/th2-codec-fix",
                "image-version": "3.4.1",
                "type": "th2-codec",
                "custom-config": {
                    "codecClassName": "com.exactpro.sf.externalapi.codec.impl.ExternalFixCodecFactory"
                },
                "pins": [
                    {
                        "name": "in_codec_encode",
                        "connection-type": "mq",
                        "attributes": [
                            "encoder_in",
                            "parsed",
                            "subscribe"
                        ]
                    },
                    {
                        "name": "out_codec_decode",
                        "connection-type": "mq",
                        "attributes": [
                            "decoder_out",
                            "parsed",
                            "publish"
                        ]
                    },
                    {
                        "name": "in_codec_general_encode",
                        "connection-type": "mq",
                        "attributes": [
                            "general_encoder_in",
                            "parsed",
                            "subscribe"
                        ]
                    },
                    {
                        "name": "out_codec_general_decode",
                        "connection-type": "mq",
                        "attributes": [
                            "general_decoder_out",
                            "parsed",
                            "publish"
                        ]
                    }
                ],
                "extended-settings": {
                    "service": {
                        "enabled": false
                    },
                    "envVariables": {
                        "JAVA_TOOL_OPTIONS": "-XX:+ExitOnOutOfMemoryError"
                    }
                }
            },
            "sourceHash": "d42736f7985cf01619e8b316b4d7b896815da5d344281be6c11e74de14daf330"
        }
    ]
}                  
```
##
__PUT/schema/{schemaName}__

__Path variable:__

*schemaName* - Name of the schema, same as the branch name.

creates a schema named {schemaName} based on master branch.

Throws exception if requested schema already exists, or is the same as master.

__Returns:__

`SchemaControllerResponse` object fot the newly created schema. 

#### Response body example:
see the response body example above. 
##
__POST/schema/{schemaName}__

__Path variables:__

*schemaName* - Name of the schema, same as the branch name.

Updates existing schema with resources provided in request body.

Throws exception if requested schema doesn't exists, or is the same as master.

__Request Body:__

need to provide request body of  `List<RequestEntry>`
```json
[
    {
        "operation": "update",
        "payload": {
            "kind": "Th2Box",
            "name": "act",
            "spec": {
                "image-name": "ghcr.io/th2-net/th2-act-template",
                "image-version": "v2.2.0-beta",
                "type": "th2-act",
                "pins": [
                    {
                        "name": "test",
                        "connection-type": "grpc",
                        "attributes": [
                            "grpc_to_verifier"
                        ]
                    }
                ],
                "prometheus": {
                    "enabled": "true"
                },
                "extended-settings": {
                    "service": {
                        "enabled": "false"
                    },
                    "envVariables": {
                        "JAVA_TOOL_OPTIONS": "-XX:+ExitOnOutOfMemoryError"
                    }
                }
            }
        }
    }
]
```
__Returns:__

`SchemaControllerResponse` object fot the newly updated schema. 

__Response body example:__

see the response body example above. 
##
##
### Subscription API
__GET/subscriptions/schema/{schemaName}__

__Path variables:__

*schemaName* - Name of the schema, same as the branch name.

Starts a subscription on requested schema.

__Returns:__

`SseEmitter` object. 
##
##
### Descriptor API

__GET/descriptor/{schema}/{kind}/{box}__

__Path variables:__

*schema* - Name of the schema, same as the branch name.

*kind* -  Kind of the box as defined in Th2 kinds enum: `Th2CoreBox`, `Th2Mstore`, `Th2Estore`, `Th2Box`

*box* -  Name of the resource

__Returns:__

value of `protobuf-description-base64` label for specified box.

__Response body examples:__

__case 1:__ `protobuf-description-base64` is present in box manifest and has __non-null__ value
```json
{
"descriptor": "protobuf-description-base64",
"content": "eyJncnBjLWNoZWNrMS0yLjIuMS5qYXIiOnsidGgyX2dycGNfY2hlY2sx ...... "
}

```

__case 2:__ `protobuf-description-base64` is __NOT__ present in box manifest or has __null__ value

Response body is empty, with `204 No Content` response code.

##
##
### Secrets API
__GET/secrets/{schema}__

__Returns:__

Set containing names of keys of custom secrets in specified schema.

__Response body example:__

If there are two keys in custom secrets file of this schema
```json
[
    "key1",
    "key2"
]

```
##
__PUT/secrets/{schema}__

__Request Body:__
```json
[
   {
    "key": "key1",
    "data": "dXBkYXRlZHZhbHVl"
   },
   {
    "key": "key2",
    "data":"c29tZS1zZWNyZXQtdmFsdWU="
   }
]
```
__Returns:__

Set containing names of created/updated keys.

__Response body example:__
```json
[
    "key1",
    "key2"
]
```
##
__DELETE/secrets/{schema}__

__RequestBody:__
```json
[
    "key1"
]
```
Deletes key from custom secrets file of specified namespace

__Returns:__

List containing names of deleted keys.

__Response body example:__
```json
[
    "key1"
]
```
##