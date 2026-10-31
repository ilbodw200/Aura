package app.aura.android;
import org.json.*;
import java.util.*;
final class ProviderFixtures {
    static String[] links(){return new String[]{
        "vless://11111111-2222-4333-8444-555555555555@server.example.com:443?type=tcp&security=tls&mode=gun&authority=front.example.com#TcpWithGrpcDefaults",
        "vless://11111111-2222-4333-8444-555555555555@server.example.com:443?type=ws&security=tls&mode=gun&authority=front.example.com#WebSocketWithGrpcDefaults",
        "hysteria2://test-password@server.example.com:443?sni=front.example.com&mode=gun#HysteriaWithGrpcDefaults",
        "hysteria2://test-password@server.example.com:443?sni=front.example.com&fm=%7B%22quicParams%22%3A%7B%22debug%22%3Afalse%2C%22congestion%22%3A%22bbr%22%7D%7D#ProviderFixture",
        "hysteria2://test-password@server.example.com:443?sni=front.example.com&fm=%7B%22tcp%22%3A%5B%7B%22type%22%3A%22fragment%22%2C%22settings%22%3A%7B%22delay%22%3A%220%22%2C%22length%22%3A%2210-20%22%2C%22packets%22%3A%22tlshello%22%7D%7D%5D%7D#ProviderFixture",
        "vless://11111111-2222-4333-8444-555555555555@server.example.com:443?encryption=none&flow=xtls-rprx-vision&type=tcp&security=reality&sni=front.example.com&fp=firefox&pbk=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA#ProviderFixture",
        "vless://11111111-2222-4333-8444-555555555555@server.example.com:443?encryption=none&flow=xtls-rprx-vision&type=tcp&security=reality&sni=front.example.com&fp=firefox&pbk=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA&fm=%7B%22tcp%22%3A%5B%7B%22type%22%3A%22fragment%22%2C%22settings%22%3A%7B%22delay%22%3A%220%22%2C%22length%22%3A%2210-20%22%2C%22packets%22%3A%22tlshello%22%7D%7D%5D%7D#ProviderFixture",
        "vless://11111111-2222-4333-8444-555555555555@server.example.com:443?encryption=none&type=grpc&serviceName=TestService&mode=gun&authority=front.example.com&security=reality&sni=front.example.com&fp=firefox&pbk=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA&fm=%7B%22tcp%22%3A%5B%7B%22type%22%3A%22fragment%22%2C%22settings%22%3A%7B%22delay%22%3A%220%22%2C%22length%22%3A%2210-20%22%2C%22packets%22%3A%22tlshello%22%7D%7D%5D%7D#ProviderFixture",
        "vless://11111111-2222-4333-8444-555555555555@server.example.com:443?encryption=none&type=ws&path=%2Ftest&host=front.example.com&security=tls&sni=front.example.com&fp=chrome&alpn=http%2F1.1#ProviderFixture",
        "vless://11111111-2222-4333-8444-555555555555@server.example.com:443?encryption=none&type=ws&path=%2Ftest&host=front.example.com&security=tls&sni=front.example.com&fp=edge&alpn=http%2F1.1&fm=%7B%22tcp%22%3A%5B%7B%22type%22%3A%22fragment%22%2C%22settings%22%3A%7B%22delay%22%3A%220%22%2C%22length%22%3A%2210-20%22%2C%22packets%22%3A%22tlshello%22%7D%7D%5D%7D#ProviderFixture"
    };}
}
