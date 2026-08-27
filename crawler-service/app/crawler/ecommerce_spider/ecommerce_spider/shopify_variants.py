"""One row per stable Shopify variant, including cursor-based high-variant products."""

import json
import math
import scrapy

from ecommerce_spider.crawl_options import resolve_currency


class ShopifyVariants:
    def parse_products(self, response):
        try:
            payload = json.loads(response.text)
            if not isinstance(payload, dict) or not isinstance(payload.get("products"), list):
                raise ValueError("products.json does not contain a products array")
        except (ValueError, TypeError) as exc:
            self.metrics.error("products_schema", str(exc))
            return
        products = payload["products"]
        current_page = response.meta.get("page", 1)
        if not products:
            if current_page == 1:
                self.metrics.confirmed_empty = True
            return
        fresh = 0
        for product in products:
            if not isinstance(product, dict) or not product.get("id"):
                self.metrics.error("product_parse", "Shopify product lacks stable ID")
                continue
            if product["id"] in self.processed_product_ids:
                self.metrics.error("catalog_pagination", "Repeated product across Shopify pages")
                continue
            self.processed_product_ids.add(product["id"])
            fresh += 1
            if self.storefront_token:
                yield self.variant_request({k: v for k, v in product.items() if k != "variants"})
                continue
            variants = product.get("variants")
            if not isinstance(variants, list) or not variants:
                self.metrics.error("product_parse", "Shopify product has no variants")
                continue
            if len(variants) >= 250 or (isinstance(product.get("variants_count"), int) and product["variants_count"] > len(variants)):
                self.metrics.error("variant_limit", "Public Shopify JSON may truncate variants; configure storefront_token for complete pagination")
            yield from self.emit_variants(product, variants)
        if len(products) == self.limit:
            if not fresh or current_page >= 200:
                self.metrics.error("catalog_pagination", "Shopify catalog repeated or reached page limit")
            else:
                self.page = current_page + 1
                yield from self.request_page()

    def emit_variants(self, product, variants):
        for variant in variants:
            self.metrics.counts["discovered"] += 1
            self.metrics.counts["fetched"] += 1
            try:
                sku = self.generate_unique_sku(product["id"], variant["id"])
                if sku in self.processed_variant_ids:
                    raise ValueError("Repeated Shopify variant ID")
                self.processed_variant_ids.add(sku)
                price = float(variant.get("price") or 0)
                if not math.isfinite(price) or price <= 0:
                    raise ValueError("Variant has no positive price")
                selections = variant.get("selectedOptions") or [
                    {"name": option.get("name", f"Option {i}"), "value": variant.get(f"option{i}")}
                    for i, option in enumerate(product.get("options") or [], 1) if isinstance(option, dict)
                ]
                selections = [s for s in selections if s.get("value") and s["value"] != "Default Title"]
                clean = lambda s: self.clean_text_regex(s).replace("^", " ").replace("#", " ").replace("|", " ")
                attrs = "|||".join(f"{clean(s['name'])}^{clean(s['value'])}" for s in selections)
                if variant.get("sku"):
                    attrs += ("|||" if attrs else "") + "Source SKU^" + clean(variant["sku"])
                title = self.clean_text_regex(product.get("title", ""))
                if selections:
                    title += " - " + " / ".join(str(s["value"]) for s in selections)
                images = product.get("images") or []
                image = variant.get("featured_image") or {}
                image = image.get("src") if isinstance(image, dict) else image
                if not image:
                    image = next((i.get("src") for i in images if variant["id"] in i.get("variant_ids", [])), "")
                if not image:
                    image = (images[0].get("src") if images else (product.get("image") or {}).get("src")) or ""
                item = self.build_item(sku, title, self.clean_description(product.get("body_html", "")), price,
                                       product.get("product_type") or "Others", image, attrs)
                if variant.get("currency"):
                    item["货币"], item["币种来源"] = resolve_currency(self, preferred=variant["currency"])
                yield item
            except (ValueError, KeyError, TypeError, AttributeError) as exc:
                self.metrics.error("variant_parse", str(exc))

    def variant_request(self, product, cursor=None, page=1):
        query = '''query Variants($id:ID!, $after:String) { product(id:$id) {
          variants(first:100, after:$after) { nodes {id sku title selectedOptions {name value}
            price {amount currencyCode} image {url} } pageInfo {hasNextPage endCursor} }
        } }'''
        return scrapy.Request(self.domain + "/api/2026-07/graphql.json", method="POST",
                              headers={"Content-Type": "application/json", "X-Shopify-Storefront-Access-Token": self.storefront_token},
                              body=json.dumps({"query": query, "variables": {"id": f"gid://shopify/Product/{product['id']}", "after": cursor}}),
                              callback=self.parse_variant_page, errback=self.products_failed,
                              meta={"product": product, "variant_page": page, "dont_redirect": True})

    def parse_variant_page(self, response):
        try:
            payload = json.loads(response.text)
            if payload.get("errors"):
                raise ValueError("Shopify Storefront GraphQL returned errors")
            connection = payload["data"]["product"]["variants"]
            nodes, info = connection["nodes"], connection["pageInfo"]
            if not isinstance(nodes, list) or not nodes or type(info["hasNextPage"]) is not bool:
                raise ValueError("Missing Shopify variant nodes/pageInfo")
            product = response.meta["product"]
            variants = [{**v, "price": v["price"]["amount"], "currency": v["price"]["currencyCode"],
                         "featured_image": {"src": (v.get("image") or {}).get("url", "")}}
                        for v in nodes]
            yield from self.emit_variants(product, variants)
            if info["hasNextPage"]:
                cursor = info["endCursor"]
                key = (product["id"], cursor)
                if not cursor or key in self.variant_cursors or response.meta["variant_page"] >= 20:
                    raise ValueError("Shopify variant cursor repeated or page limit reached")
                self.variant_cursors.add(key)
                yield self.variant_request(product, cursor, response.meta["variant_page"] + 1)
        except (ValueError, KeyError, TypeError, AttributeError) as exc:
            self.metrics.error("variant_api", str(exc))
