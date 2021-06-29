### Descriptor API
#### GET/descriptor/{schema}/{kind}/{box}
__Path variables:__

*schema* - Name of the schema, same as the branch name.

*kind* -  Kind of the box as defined in Th2 kinds enum: `Th2CoreBox`, `Th2Mstore`, `Th2Estore`, `Th2Box`

*box* -  Name of the resource

__Returns:__

value of `protobuf-description-base64` label for specified box.

#### Response body examples:

__case 1:__ `protobuf-description-base64` is present in box manifest and has __non-null__ value
```json
{
"descriptor": "protobuf-description-base64",
"content": "eyJncnBjLWNoZWNrMS0yLjIuMS5qYXIiOnsidGgyX2dycGNfY2hlY2sx ...... "
}

```

__case 2:__ `protobuf-description-base64` is __NOT__ present in box manifest or has __null__ value

Response body is empty, with `204 No Content` response code.


### Secrets API
#### GET/secrets/{schema}

__Returns:__

Set containing names of keys of custom secrets in specified schema.

#### Response body example

If there are two keys in custom secrets file of this schema
```json
[
    "key1",
    "key2"
]

```
 
#### PUT/secrets/{schema}
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

### Response body example:
```json
[
    "key1",
    "key2"
]
```
 
### DELETE/secrets/{schema}
__RequestBody:__
```json
[
    "key1"
]
```
Deletes key from custom secrets file of specified namespace
### Returns
List containing names of deleted keys.

### Response body example:
```json
[
    "key1"
]
```
##