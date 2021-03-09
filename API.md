## Descriptor API
### GET/descriptor/{schema}/{kind}/{box}
### Path variables
__schema:__ Name of the schema without specific __NAMESPACE_PREFIX__. Same as the branch name.

__kind:__  Kind of the box as defined in Th2 kinds enum. _("Th2CoreBox", "Th2Mstore", "Th2Estore", "Th2Box")_

__box:__  Name of the resource

### Returns
value of "protobuf-description-base64" label for specified box.

### Response body example

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