package io.github.mebsic.core.util;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;

public final class DomainSettingsStore {
    public static final String COLLECTION_NAME = "proxy_settings";
    public static final String DOMAIN_DOCUMENT_ID = "domain";
    public static final String DOMAIN_FIELD = "domain";

    private DomainSettingsStore() {
    }

    public static void ensureDomainDocument(MongoCollection<Document> collection) {
        if (collection == null) {
            return;
        }
        Document defaults = new Document(DOMAIN_FIELD, NetworkConstants.DEFAULT_DOMAIN);
        collection.updateOne(
                new Document("_id", DOMAIN_DOCUMENT_ID),
                new Document("$setOnInsert", defaults),
                new UpdateOptions().upsert(true)
        );
    }

    public static boolean refreshDomain(MongoCollection<Document> collection) {
        if (collection == null) {
            return false;
        }
        Document domainDoc = collection.find(new Document("_id", DOMAIN_DOCUMENT_ID))
                .projection(new Document(DOMAIN_FIELD, 1))
                .first();
        String resolved = readDomain(domainDoc);
        return NetworkConstants.setDomain(resolved);
    }

    private static String readDomain(Document domainDoc) {
        if (domainDoc == null) {
            return NetworkConstants.DEFAULT_DOMAIN;
        }
        Object raw = domainDoc.get(DOMAIN_FIELD);
        if (raw == null) {
            return NetworkConstants.DEFAULT_DOMAIN;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return NetworkConstants.DEFAULT_DOMAIN;
        }
        return text;
    }
}
