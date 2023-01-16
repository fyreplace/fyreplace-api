.PHONY: static migrations emails

static:
	poetry run python manage.py collectstatic --no-input

migrations:
	poetry run python manage.py migrate

emails:
	npx mjml users/templates/*.html.mjml -c.minify=true -o users/templates
