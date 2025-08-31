package soia

class Method<Request, Response>(
    val name: String,
    number: Int,
    requestSerializer: Serializer<Request>,
    responseSerializer: Serializer<Response>,
)
