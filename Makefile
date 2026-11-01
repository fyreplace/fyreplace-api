.PHONY: emails keygen-rsa

emails:
	for email_path in src/main/resources/templates/*/html.html.mjml; \
	do \
		npx mjml \
		    -c.minify=true \
		    -c.allowIncludes=true \
		    -c.includePath=src/main/resources/templates/ \
		    -o $${email_path%.mjml} \
		    $$email_path; \
	done

keygen-rsa:
	mkdir -p src/main/resources/keys
	openssl genrsa > src/main/resources/keys/jwt.rsa 2048
	openssl pkcs8 -topk8 -nocrypt -inform pem -in src/main/resources/keys/jwt.rsa -outform pem > src/main/resources/keys/jwt.rsa.pem
	openssl rsa -in src/main/resources/keys/jwt.rsa -pubout > src/main/resources/keys/jwt.rsa.pub
